--[[--
Booklore KOReader Plugin

Syncs reading sessions to Booklore server via REST API.

@module koplugin.BookloreSync
--]]--

local DataStorage = require("datastorage")
local Dispatcher = require("dispatcher")
local EventListener = require("ui/widget/eventlistener")
local InfoMessage = require("ui/widget/infomessage")
local InputDialog = require("ui/widget/inputdialog")
local LuaSettings = require("luasettings")
local SQ3 = require("lua-ljsqlite3/init")
local UIManager = require("ui/uimanager")
local WidgetContainer = require("ui/widget/container/widgetcontainer")
local ConfirmBox = require("ui/widget/confirmbox")
local Settings = require("settings")

local base_logger = require("logger")
local log_to_file_flag = false
local log_file_path = DataStorage:getDataDir() .. "/plugins/booklore.koplugin/booklore_sync.log"

local function append_log(level, ...)
    if not log_to_file_flag then
        return
    end
    local f = io.open(log_file_path, "a")
    if not f then
        return
    end
    local parts = {}
    for i = 1, select('#', ...) do
        parts[i] = tostring(select(i, ...))
    end
    f:write(os.date("%Y-%m-%d %H:%M:%S"), " [", level:upper(), "] ", table.concat(parts, " "), "\n")
    f:close()
end

local logger = {}
for _, level in ipairs({ "dbg", "info", "warn", "err", "fatal" }) do
    logger[level] = function(...)
        base_logger[level](...)
        append_log(level, ...)
    end
end
local _ = require("gettext")
local T = require("ffi/util").template

local BookloreSync = WidgetContainer:extend{
    name = "booklore",
    is_doc_only = false,
}

function BookloreSync:init()
    self.settings = LuaSettings:open(DataStorage:getSettingsDir() .. "/booklore.lua")
    
    self.server_url = self.settings:readSetting("server_url") or ""
    self.username = self.settings:readSetting("username") or ""
    self.password = self.settings:readSetting("password") or ""
    self.is_enabled = self.settings:readSetting("is_enabled") or false
    self.min_duration = self.settings:readSetting("min_duration") or 30
    self.progress_decimal_places = self.settings:readSetting("progress_decimal_places") or 2
    self.log_to_file = self.settings:readSetting("log_to_file") or false
    log_to_file_flag = self.log_to_file
    self.historical_sync_ack = self.settings:readSetting("historical_sync_ack") or false
    
    -- Sync options
    self.force_push_session_on_suspend = self.settings:readSetting("force_push_session_on_suspend") or false
    self.connect_network_on_suspend = self.settings:readSetting("connect_network_on_suspend") or false
    self.manual_sync_only = self.settings:readSetting("manual_sync_only") or false
    self.silent_messages = self.settings:readSetting("silent_messages") or false
    
    -- Persistent local database for offline support
    self.local_db = LuaSettings:open(DataStorage:getSettingsDir() .. "/booklore_db.lua")
    
    -- Cache for book hash -> bookId mapping and file path -> hash mapping (persisted)
    local cached_data = self.local_db:readSetting("book_cache") or {}
    
    -- Migrate from old cache structure if needed
    if cached_data.file_hashes == nil and cached_data.book_ids == nil then
        -- Old structure: flat mapping of hash -> id, need to migrate
        logger.info("BookloreSync: Migrating old cache structure to new format")
        local old_cache = cached_data
        self.book_cache = {
            file_hashes = {},  -- file_path -> hash
            book_ids = old_cache,  -- hash -> book_id
        }
    else
        -- New structure or empty
        self.book_cache = cached_data
        if not self.book_cache.file_hashes then
            self.book_cache.file_hashes = {}
        end
        if not self.book_cache.book_ids then
            self.book_cache.book_ids = {}
        end
    end
    
    -- Current reading session tracking
    self.current_session = nil
    
    -- Pending sessions queue (for offline sync)
    self.pending_sessions = self.local_db:readSetting("pending_sessions") or {}
    
    -- Register menu
    self.ui.menu:registerToMainMenu(self)
    
    -- Register actions with Dispatcher for gesture manager integration
    self:registerDispatcherActions()
end

function BookloreSync:registerDispatcherActions()
    -- Register Toggle Sync action
    Dispatcher:registerAction("booklore_toggle_sync", {
        category = "none",
        event = "ToggleBookloreSync",
        title = _("Toggle Booklore Sync"),
        general = true,
    })
    
    -- Register Sync Pending Sessions action
    Dispatcher:registerAction("booklore_sync_pending", {
        category = "none",
        event = "SyncBooklorePending",
        title = _("Sync Booklore Pending Sessions"),
        general = true,
    })

    -- Register Manual Sync Only toggle action
    Dispatcher:registerAction("booklore_toggle_manual_sync_only", {
        category = "none",
        event = "ToggleBookloreManualSyncOnly",
        title = _("Toggle Booklore Manual Sync Only"),
        general = true,
    })
    
    -- Register Test Connection action
    Dispatcher:registerAction("booklore_test_connection", {
        category = "none",
        event = "TestBookloreConnection",
        title = _("Test Booklore Connection"),
        general = true,
    })
end

function BookloreSync:roundProgress(value)
    local multiplier = 10 ^ self.progress_decimal_places
    return math.floor(value * multiplier + 0.5) / multiplier
end

function BookloreSync:toggleSync()
    self.is_enabled = not self.is_enabled
    self.settings:saveSetting("is_enabled", self.is_enabled)
    self.settings:flush()
    UIManager:show(InfoMessage:new{
        text = self.is_enabled and _("Booklore sync enabled") or _("Booklore sync disabled"),
        timeout = 1,
    })
end

function BookloreSync:toggleManualSyncOnly()
    self.manual_sync_only = not self.manual_sync_only
    self.settings:saveSetting("manual_sync_only", self.manual_sync_only)
    
    -- If enabling manual_sync_only, disable force_push
    if self.manual_sync_only and self.force_push_session_on_suspend then
        self.force_push_session_on_suspend = false
        self.settings:saveSetting("force_push_session_on_suspend", false)
    end
    
    self.settings:flush()
    local message
    if self.manual_sync_only then
        message = _("Manual sync only: sessions will be cached until you sync pending sessions manually")
    else
        message = _("Manual sync only disabled: automatic syncing restored where enabled")
    end
    UIManager:show(InfoMessage:new{
        text = message,
        timeout = 2,
    })
end

-- Event handlers for Dispatcher actions
function BookloreSync:onToggleBookloreSync()
    self:toggleSync()
    return true
end

function BookloreSync:onSyncBooklorePending()
    if #self.pending_sessions > 0 and self.is_enabled then
        self:syncPendingSessions()
    else
        if #self.pending_sessions == 0 then
            UIManager:show(InfoMessage:new{
                text = _("No pending sessions to sync"),
                timeout = 1,
            })
        end
    end
    return true
end

function BookloreSync:onTestBookloreConnection()
    self:testConnection()
    return true
end

function BookloreSync:onToggleBookloreManualSyncOnly()
    self:toggleManualSyncOnly()
    return true
end

function BookloreSync:addToMainMenu(menu_items)
    local base_menu = Settings:buildMenu(self)
    
    -- Add additional menu sections
    table.insert(base_menu, {
        text = _("Session Management"),
        sub_item_table = {
                    {
                        text = _("Minimum Session Duration"),
                        help_text = _("Set the minimum number of seconds a reading session must last to be synced. Sessions shorter than this will be discarded. Default is 30 seconds."),
                        keep_menu_open = true,
                        callback = function()
                            Settings:configureMinDuration(self)
                        end,
                    },
                    {
                        text = _("Progress Decimal Places"),
                        help_text = _("Set the number of decimal places to use when reporting reading progress percentage (0-5). Higher precision may be useful for large books. Default is 2."),
                        keep_menu_open = true,
                        callback = function()
                            Settings:configureProgressDecimalPlaces(self)
                        end,
                    },
                    {
                        text = _("Sync Pending Sessions"),
                        help_text = _("Manually sync all sessions that failed to upload previously. Sessions are cached locally when the network is unavailable and synced automatically on resume."),
                        enabled_func = function()
                            return #self.pending_sessions > 0
                        end,
                        callback = function()
                            self:syncPendingSessions()
                        end,
                    },
                    {
                        text = _("Clear Pending Sessions"),
                        help_text = _("Delete all locally cached sessions that are waiting to be synced. Use this if you want to discard pending sessions instead of uploading them."),
                        enabled_func = function()
                            return #self.pending_sessions > 0
                        end,
                        callback = function()
                            self.pending_sessions = {}
                            self.local_db:saveSetting("pending_sessions", self.pending_sessions)
                            self.local_db:flush()
                            UIManager:show(InfoMessage:new{
                                text = _("Pending sessions cleared"),
                                timeout = 2,
                            })
                        end,
                    },
                    {
                        text = _("View Pending Count"),
                        help_text = _("Display the number of reading sessions currently cached locally and waiting to be synced to the server."),
                        callback = function()
                            UIManager:show(InfoMessage:new{
                                text = T(_("%1 sessions pending sync"), #self.pending_sessions),
                                timeout = 2,
                            })
                        end,
                    },
                    {
                        text = _("View Cache Status"),
                        help_text = _("Display statistics about the local cache: number of book hashes cached, file paths cached, and pending sessions. The cache improves performance by avoiding redundant hash calculations."),
                        callback = function()
                            local hash_count = 0
                            if self.book_cache.book_ids then
                                for _ in pairs(self.book_cache.book_ids) do
                                    hash_count = hash_count + 1
                                end
                            end
                            local path_count = 0
                            if self.book_cache.file_hashes then
                                for _ in pairs(self.book_cache.file_hashes) do
                                    path_count = path_count + 1
                                end
                            end
                            UIManager:show(InfoMessage:new{
                                text = T(_("Cached hashes: %1\nCached paths: %2\nPending sessions: %3"), hash_count, path_count, #self.pending_sessions),
                                timeout = 3,
                            })
                        end,
                    },
                    {
                        text = _("Clear Local Cache"),
                        help_text = _("Delete all cached book hashes and file path mappings. This will not affect pending sessions. The cache will be rebuilt as you read. Use this if you encounter book identification issues."),
                        enabled_func = function()
                            local hash_count = 0
                            if self.book_cache.book_ids then
                                for _ in pairs(self.book_cache.book_ids) do
                                    hash_count = hash_count + 1
                                end
                            end
                            local path_count = 0
                            if self.book_cache.file_hashes then
                                for _ in pairs(self.book_cache.file_hashes) do
                                    path_count = path_count + 1
                                end
                            end
                            return hash_count > 0 or path_count > 0
                        end,
                        callback = function()
                            self.book_cache = {
                                file_hashes = {},
                                book_ids = {},
                            }
                            self.local_db:saveSetting("book_cache", self.book_cache)
                            self.local_db:flush()
                            UIManager:show(InfoMessage:new{
                                text = _("Local book cache cleared"),
                                timeout = 2,
                            })
                        end,
                    },
                },
            })
    
    table.insert(base_menu, {
        text = _("Sync Options"),
        sub_item_table = {
            {
                text = _("Only manual syncs"),
                help_text = _("Cache all sessions and prevent automatic syncing. Use 'Sync Pending Sessions' (menu or gesture) when you want to upload. Mutually exclusive with 'Force push on suspend'."),
                checked_func = function()
                    return self.manual_sync_only
                end,
                callback = function()
                    self:toggleManualSyncOnly()
                end,
            },
            {
                text = _("Force push session on suspend"),
                help_text = _("Automatically sync the current reading session and all pending sessions when the device suspends. Enables 'Connect network on suspend' option and requires network connectivity. Mutually exclusive with 'Only manual syncs'."),
                checked_func = function()
                    return self.force_push_session_on_suspend
                end,
                callback = function()
                    self.force_push_session_on_suspend = not self.force_push_session_on_suspend
                    self.settings:saveSetting("force_push_session_on_suspend", self.force_push_session_on_suspend)
                    
                    -- If enabling force_push, disable manual_sync_only
                    if self.force_push_session_on_suspend and self.manual_sync_only then
                        self.manual_sync_only = false
                        self.settings:saveSetting("manual_sync_only", false)
                    end
                    
                    self.settings:flush()
                    UIManager:show(InfoMessage:new{
                        text = self.force_push_session_on_suspend and _("Will force push session on suspend if network available") or _("Force push on suspend disabled"),
                        timeout = 2,
                    })
                end,
            },
            {
                text = _("Connect network on suspend"),
                help_text = _("Automatically enable WiFi and attempt to connect when the device suspends. Waits up to 15 seconds for connection. Useful for syncing when going offline."),
                checked_func = function()
                    return self.connect_network_on_suspend
                end,
                callback = function()
                    self.connect_network_on_suspend = not self.connect_network_on_suspend
                    self.settings:saveSetting("connect_network_on_suspend", self.connect_network_on_suspend)
                    self.settings:flush()
                    UIManager:show(InfoMessage:new{
                        text = self.connect_network_on_suspend and _("Will enable and scan for network on suspend (15s timeout)") or _("Connect network on suspend disabled"),
                        timeout = 2,
                    })
                end,
            },
        },
    })
    
    table.insert(base_menu, {
        text = _("Sync Historical Data"),
        help_text = _("One-time sync of all reading sessions from KOReader's statistics database. This reads from statistics.sqlite3 and uploads historical sessions. Warning: May create duplicate sessions if run multiple times."),
        enabled_func = function()
            return self.server_url ~= "" and self.username ~= "" and self.is_enabled
        end,
        callback = function()
            self:syncHistoricalData()
        end,
    })
    
    menu_items.booklore_sync = {
        text = _("Booklore Sync"),
        sorting_hint = "tools",
        sub_item_table = base_menu,
    }
end

function BookloreSync:enableAndScanForNetwork()
    -- Enable WiFi and scan for networks with a 15-second timeout
    logger.info("BookloreSync: Attempting to enable and connect to network")
    
    local NetworkMgr = require("ui/network/manager")
    
    -- Check if WiFi is already enabled and connected
    if NetworkMgr:isConnected() then
        logger.info("BookloreSync: Network already connected")
        return true
    end
    
    -- Try to enable WiFi
    if not NetworkMgr:isWifiOn() then
        logger.info("BookloreSync: Enabling WiFi")
        NetworkMgr:turnOnWifi()
    end
    
    -- Wait up to 15 seconds for connection
    local max_attempts = 15
    local delay = 1  -- 1 second between attempts
    
    for attempt = 1, max_attempts do
        logger.info("BookloreSync: Waiting for network connection, attempt", attempt, "of", max_attempts)
        
        if NetworkMgr:isConnected() then
            logger.info("BookloreSync: Network connected on attempt", attempt)
            return true
        end
        
        if attempt < max_attempts then
            os.execute("sleep " .. tostring(delay))
        end
    end
    
    logger.warn("BookloreSync: Failed to connect to network after", max_attempts, "seconds")
    return false
end

function BookloreSync:isNetworkConnected()
    -- Quick check to see if network is available
    logger.info("BookloreSync: Checking network connectivity")
    
    local socket = require("socket")
    local http = require("socket.http")
    local ltn12 = require("ltn12")
    
    if not self.server_url or self.server_url == "" then
        logger.warn("BookloreSync: No server URL configured")
        return false
    end
    
    -- Try a quick connection with short timeout
    http.TIMEOUT = 3
    
    local url = self.server_url
    if url:sub(-1) == "/" then
        url = url:sub(1, -2)
    end
    url = url .. "/api/health"
    
    local response_body = {}
    local res, code = http.request{
        url = url,
        method = "GET",
        sink = ltn12.sink.table(response_body),
    }
    
    if code == 200 or code == 404 then  -- 404 is ok as long as we got a response
        logger.info("BookloreSync: Network is available (code: " .. tostring(code) .. ")")
        return true
    else
        logger.warn("BookloreSync: Network check failed (code: " .. tostring(code) .. ")")
        return false
    end
end



function BookloreSync:testConnection()
    UIManager:show(InfoMessage:new{
        text = _("Testing connection..."),
        timeout = 1,
    })
    
    local socket = require("socket")
    local http = require("socket.http")
    local ltn12 = require("ltn12")
    
    -- Set timeout
    http.TIMEOUT = 5
    
    -- Use KOReader auth endpoint
    local url = self.server_url .. "/api/koreader/users/auth"
    
    -- Calculate MD5 hash of password
    local md5 = require("ffi/sha2").md5
    local password_hash = md5(self.password)
    
    local response_body = {}
    
    local res, code, response_headers = http.request{
        url = url,
        method = "GET",
        sink = ltn12.sink.table(response_body),
        headers = {
            ["x-auth-user"] = self.username,
            ["x-auth-key"] = password_hash,
        },
    }
    
    if code == 200 then
        UIManager:show(InfoMessage:new{
            text = _("Connection successful!"),
            timeout = 2,
        })
    else
        local error_msg = code or res or "unknown"
        UIManager:show(InfoMessage:new{
            text = T(_("Connection failed: %1"), tostring(error_msg)),
            timeout = 3,
        })
    end
end

-- Event handlers for tracking reading sessions
function BookloreSync:onReaderReady()
    if not self.is_enabled or self.server_url == "" then
        return
    end
    
    logger.info("BookloreSync: Reader ready, starting session tracking")
    
    -- Try to sync pending sessions when reader becomes ready (attempt directly, silently)
    if not self.manual_sync_only and #self.pending_sessions > 0 then
        logger.info("BookloreSync: Attempting to sync pending sessions on reader ready")
        self:syncPendingSessions(true)  -- silent=true
    elseif self.manual_sync_only and #self.pending_sessions > 0 then
        logger.info("BookloreSync: Manual sync only enabled, leaving pending sessions queued")
    end
    
    self:startSession()
end

function BookloreSync:onCloseDocument()
    if not self.is_enabled then
        return
    end
    
    logger.info("BookloreSync: Document closing, ending session")
    self:endSession({ force_queue = self.manual_sync_only })
end

function BookloreSync:onSuspend()
    if not self.is_enabled then
        return
    end
    
    logger.info("BookloreSync: Device suspending, ending session")

    -- In manual-only mode, never attempt auto sync on suspend
    if self.manual_sync_only then
        logger.info("BookloreSync: Manual sync only enabled, queueing session without syncing")
        if self.current_session then
            self:endSession({ silent = true, force_queue = true })
        end
        return
    end
    
    -- Determine if we should try to push sessions
    local should_force_push = self.force_push_session_on_suspend
    local network_available = false
    
    if should_force_push then
        -- Check if network is already connected
        network_available = self:isNetworkConnected()
        
        -- If not connected but connect_network_on_suspend is enabled, try to connect
        if not network_available and self.connect_network_on_suspend then
            logger.info("BookloreSync: Network not connected, attempting to enable and connect")
            network_available = self:enableAndScanForNetwork()
        end
        
        if network_available then
            logger.info("BookloreSync: Force push enabled and network available")
            
            -- First, try to sync any pending sessions
            if #self.pending_sessions > 0 then
                logger.info("BookloreSync: Attempting to sync", #self.pending_sessions, "pending sessions before suspend")
                self:syncPendingSessions(true)  -- silent=true
            end
            
            -- Then end current session without queueing (try to send immediately)
            if self.current_session then
                logger.info("BookloreSync: Ending current session with immediate send")
                self:endSession({ silent = true, force_queue = false })
            end
        else
            logger.info("BookloreSync: Force push enabled but no network, queuing normally")
            if self.current_session then
                self:endSession({ silent = true, force_queue = true })
            end
        end
    else
        -- Default behavior: persist the session locally
        logger.info("BookloreSync: Normal suspend, queueing session")
        if self.current_session then
            self:endSession({ silent = true, force_queue = true })
        end
    end
end

function BookloreSync:onResume()
    if not self.is_enabled or self.server_url == "" then
        return
    end
    
    logger.info("BookloreSync: Device resuming from sleep")
    
    -- Quick network check before attempting to sync
    if not self.manual_sync_only and #self.pending_sessions > 0 then
        if self:isNetworkConnected() then
            logger.info("BookloreSync: Network available, attempting to sync pending sessions on resume")
            self:syncPendingSessions(true)  -- silent=true
        else
            logger.info("BookloreSync: Network unavailable, skipping sync attempt on resume")
        end
    elseif self.manual_sync_only and #self.pending_sessions > 0 then
        logger.info("BookloreSync: Manual sync only enabled, skipping auto sync on resume")
    end
    
    -- If a book is currently open, start a new session
    if self.ui and self.ui.document then
        logger.info("BookloreSync: Book is open, starting new session after wake")
        self:startSession()
    else
        logger.info("BookloreSync: No book open after resume")
    end
end

function BookloreSync:startSession()
    logger.info("BookloreSync: ========== Starting session ==========")
    
    if not self.ui or not self.ui.document then
        logger.warn("BookloreSync: No document available")
        return
    end
    
    local file_path = self.ui.document.file
    if not file_path then
        logger.warn("BookloreSync: No file path available")
        return
    end
    
    logger.info("BookloreSync: Document file path:", file_path)
    
    -- Check if we have a cached hash for this file path
    logger.info("BookloreSync: Checking cache for file path")
    local book_hash = self.book_cache.file_hashes and self.book_cache.file_hashes[file_path]
    
    if book_hash then
        logger.info("BookloreSync: Found cached hash for file:", book_hash)
    else
        logger.info("BookloreSync: Hash not in cache, calculating MD5 for:", file_path)
        -- Hash not in cache, calculate it
        book_hash = self:calculateBookHash(file_path)
        if not book_hash then
            logger.warn("BookloreSync: Failed to calculate book hash")
            return
        end
        
        -- Cache the hash for this file path
        if not self.book_cache.file_hashes then
            self.book_cache.file_hashes = {}
        end
        self.book_cache.file_hashes[file_path] = book_hash
        self.local_db:saveSetting("book_cache", self.book_cache)
        self.local_db:flush()
        logger.info("BookloreSync: Calculated and cached hash for file:", book_hash)
    end
    
    logger.info("BookloreSync: Book MD5 hash:", book_hash)
    
    -- Look up book ID (from cache or server)
    local book_id = self:getBookIdByHash(book_hash)
    if not book_id then
        logger.warn("BookloreSync: Failed to get book ID (offline and not in cache)")
        logger.info("BookloreSync: Will track session with hash for later resolution")
        -- Don't return - continue with nil book_id, we'll store the hash
    else
        logger.info("BookloreSync: Book ID:", book_id)
    end
    
    -- Get current reading position
    local start_progress = 0
    local start_location = "0"
    
    if self.ui.document then
        if self.ui.document.info and self.ui.document.info.has_pages then
            -- PDF or image-based format
            local current_page = self.ui.document:getCurrentPage()
            local total_pages = self.ui.document:getPageCount()
            start_progress = self:roundProgress((current_page / total_pages) * 100)
            start_location = tostring(current_page)
        elseif self.ui.rolling then
            -- EPUB or reflowable format
            local cur_page = self.ui.document:getCurrentPage()
            local total_pages = self.ui.document:getPageCount()
            start_progress = self:roundProgress((cur_page / total_pages) * 100)
            start_location = tostring(cur_page)
        end
    end
    
    -- Store session info (book_id may be nil if offline)
    self.current_session = {
        book_id = book_id,
        book_hash = book_hash,
        start_time = os.time(),
        file_path = file_path,
        start_progress = start_progress,
        start_location = start_location,
    }
    
    if book_id then
        logger.info("BookloreSync: Session started successfully at progress:", start_progress)
    else
        logger.info("BookloreSync: Session started (offline mode) at progress:", start_progress)
        UIManager:show(InfoMessage:new{
            text = _("Tracking offline - will sync when online"),
            timeout = 2,
        })
    end
end

function BookloreSync:calculateBookHash(file_path)
    logger.info("BookloreSync: Calculating MD5 hash for:", file_path)
    
    local file = io.open(file_path, "rb")
    if not file then
        logger.warn("BookloreSync: Could not open file for hashing")
        return nil
    end
    
    local md5 = require("ffi/sha2").md5
    local base = 1024
    local block_size = 1024
    local buffer = {}
    
    -- Get file size
    local file_size = file:seek("end")
    file:seek("set", 0)
    
    logger.info("BookloreSync: File size:", file_size)
    
    -- Sample file at specific positions (matching Booklore's FileFingerprint algorithm)
    -- Positions: base << (2*i) for i from -1 to 10
    for i = -1, 10 do
        local position = bit.lshift(base, 2 * i)
        
        if position >= file_size then
            break
        end
        
        file:seek("set", position)
        local chunk = file:read(block_size)
        if chunk then
            table.insert(buffer, chunk)
        end
    end
    
    file:close()
    
    -- Calculate MD5 of all sampled chunks
    local combined_data = table.concat(buffer)
    local hash = md5(combined_data)
    
    logger.info("BookloreSync: Hash calculated:", hash)
    return hash
end

function BookloreSync:getBookIdByHash(book_hash)
    logger.info("BookloreSync: Looking up book ID for hash:", book_hash)
    
    -- Check local cache first
    if self.book_cache.book_ids and self.book_cache.book_ids[book_hash] then
        logger.info("BookloreSync: Found book ID in local cache:", tostring(self.book_cache.book_ids[book_hash]))
        return self.book_cache.book_ids[book_hash]
    end
    
    local url = self.server_url
    if url:sub(-1) == "/" then
        url = url:sub(1, -2)
    end
    url = url .. "/api/koreader/books/by-hash/" .. book_hash
    
    logger.info("BookloreSync: Request URL:", url)
    
    -- Calculate MD5 hash of password for auth
    local md5 = require("ffi/sha2").md5
    local password_hash = md5(self.password)
    
    local http = require("socket.http")
    local ltn12 = require("ltn12")
    local json = require("json")
    
    http.TIMEOUT = 10
    
    local response_body = {}
    local res, code, response_headers = http.request{
        url = url,
        method = "GET",
        sink = ltn12.sink.table(response_body),
        headers = {
            ["x-auth-user"] = self.username,
            ["x-auth-key"] = password_hash,
        },
    }
    
    logger.info("BookloreSync: Response code:", tostring(code))
    
    if code ~= 200 then
        logger.warn("BookloreSync: Failed to get book ID, code:", tostring(code))
        return nil
    end
    
    local response_text = table.concat(response_body)
    logger.info("BookloreSync: Response body:", response_text)
    
    local success, book_data = pcall(json.decode, response_text)
    if not success or not book_data then
        logger.warn("BookloreSync: Failed to parse JSON response")
        return nil
    end
    
    if book_data.id then
        logger.info("BookloreSync: Found book ID:", tostring(book_data.id))
        
        -- Cache the result for offline use
        if not self.book_cache.book_ids then
            self.book_cache.book_ids = {}
        end
        self.book_cache.book_ids[book_hash] = book_data.id
        self.local_db:saveSetting("book_cache", self.book_cache)
        self.local_db:flush()
        logger.info("BookloreSync: Cached book ID for offline use")
        
        return book_data.id
    end
    
    logger.warn("BookloreSync: No book ID in response")
    return nil
end

function BookloreSync:endSession(options)
    options = options or {}
    local silent = options.silent or false
    local force_queue = options.force_queue or false

    -- In manual-only mode, always queue sessions until the user initiates sync
    if self.manual_sync_only then
        force_queue = true
    end

    if not self.current_session then
        logger.info("BookloreSync: No active session to end")
        return
    end
    
    logger.info("BookloreSync: Ending session for book ID:", self.current_session.book_id)
    logger.info("BookloreSync: Session started at:", self.current_session.start_time)
    
    -- Get current reading position
    local end_progress = 0
    local end_location = "0"
    
    if self.ui.document then
        if self.ui.document.info and self.ui.document.info.has_pages then
            -- PDF or image-based format
            local current_page = self.ui.document:getCurrentPage()
            local total_pages = self.ui.document:getPageCount()
            end_progress = self:roundProgress((current_page / total_pages) * 100)
            end_location = tostring(current_page)
        elseif self.ui.rolling then
            -- EPUB or reflowable format
            local cur_page = self.ui.document:getCurrentPage()
            local total_pages = self.ui.document:getPageCount()
            end_progress = self:roundProgress((cur_page / total_pages) * 100)
            end_location = tostring(cur_page)
        end
    end
    
    local end_time = os.time()
    local duration_seconds = end_time - self.current_session.start_time
    
    -- Don't record sessions shorter than minimum duration (likely false triggers) / 0 pages read
    if duration_seconds < self.min_duration then
        logger.info("BookloreSync: Session too short, not recording (", duration_seconds, "s, minimum is ", self.min_duration, "s)")
        self.current_session = nil
        return
    end
    if end_progress <= self.current_session.start_progress then
        logger.info("BookloreSync: No progress made, not recording (start:", self.current_session.start_progress, "%, end:", end_progress, "%)")
        self.current_session = nil
        return
    end
    
    local progress_delta = self:roundProgress(end_progress - self.current_session.start_progress)
    
    -- Determine book type from file extension
    local book_type = "EPUB"
    if self.current_session.file_path then
        local ext = self.current_session.file_path:match("^.+%.(.+)$")
        if ext then
            ext = ext:upper()
            if ext == "PDF" then
                book_type = "PDF"
            end
        end
    end
    
    -- Prepare session data
    local session_data = {
        bookId = self.current_session.book_id,
        bookHash = self.current_session.book_hash,  -- Store hash for offline resolution
        bookType = book_type,
        startTime = self:formatTimestamp(self.current_session.start_time),
        endTime = self:formatTimestamp(end_time),
        durationSeconds = duration_seconds,
        startProgress = self.current_session.start_progress,
        endProgress = end_progress,
        progressDelta = progress_delta,
        startLocation = self.current_session.start_location,
        endLocation = end_location,
    }
    
    logger.info("BookloreSync: Session data prepared - duration:", duration_seconds, "s, progress:", progress_delta)
    
    -- If we don't have a book ID, or we're intentionally queuing, store for later sync
    if force_queue or not self.current_session.book_id then
        if not self.current_session.book_id then
            logger.info("BookloreSync: No book ID - queuing session for offline sync")
        else
            logger.info("BookloreSync: Force-queuing session for later sync")
        end
        table.insert(self.pending_sessions, session_data)
        self.local_db:saveSetting("pending_sessions", self.pending_sessions)
        self.local_db:flush()
        if not silent and not self.silent_messages then
            UIManager:show(InfoMessage:new{
                text = T(_("Session saved offline (%1 pending)"), #self.pending_sessions),
                timeout = 3,
            })
        end
        self.current_session = nil
        return
    end
    
    -- Send session to server
    self:sendSessionToServer(session_data, silent)
    
    -- Clear current session
    self.current_session = nil
end

function BookloreSync:formatTimestamp(unix_time)
    -- Convert Unix timestamp to ISO 8601 format for Java Instant
    -- Format: YYYY-MM-DDTHH:MM:SSZ
    return os.date("!%Y-%m-%dT%H:%M:%SZ", unix_time)
end

function BookloreSync:sendSessionToServer(session_data, silent)
    silent = silent or false
    logger.info("BookloreSync: Sending session to server")
    
    local url = self.server_url
    if url:sub(-1) == "/" then
        url = url:sub(1, -2)
    end
    url = url .. "/api/v1/reading-sessions"
    
    logger.info("BookloreSync: POST URL:", url)
    
    -- Calculate MD5 hash of password for auth
    local md5 = require("ffi/sha2").md5
    local password_hash = md5(self.password)
    
    local http = require("socket.http")
    local ltn12 = require("ltn12")
    local json = require("json")
    
    http.TIMEOUT = 10
    
    -- Encode session data as JSON
    local json_data = json.encode(session_data)
    logger.info("BookloreSync: Request payload:", json_data)
    
    local response_body = {}
    local res, code, response_headers = http.request{
        url = url,
        method = "POST",
        sink = ltn12.sink.table(response_body),
        source = ltn12.source.string(json_data),
        headers = {
            ["x-auth-user"] = self.username,
            ["x-auth-key"] = password_hash,
            ["Content-Type"] = "application/json",
            ["Content-Length"] = tostring(#json_data),
        },
    }
    
    logger.info("BookloreSync: Response code:", tostring(code))
    
    if code == 202 or code == 200 then
        logger.info("BookloreSync: Session recorded successfully")
        if not silent then
            UIManager:show(InfoMessage:new{
                text = _("Reading session synced to Booklore"),
                timeout = 2,
            })
        end
        
        -- If we have pending sessions, try to sync them now
        if #self.pending_sessions > 0 then
            logger.info("BookloreSync: Successful sync detected, attempting to sync pending sessions")
            self:syncPendingSessions(silent)
        end
        
        return true
    else
        logger.warn("BookloreSync: Failed to record session, code:", tostring(code))
        local response_text = table.concat(response_body)
        if response_text and response_text ~= "" then
            logger.warn("BookloreSync: Response:", response_text)
        else
            logger.warn("BookloreSync: Empty response body")
        end
        
        -- Queue the session for later retry
        table.insert(self.pending_sessions, session_data)
        self.local_db:saveSetting("pending_sessions", self.pending_sessions)
        self.local_db:flush()
        
        logger.info("BookloreSync: Session queued for retry (", #self.pending_sessions, " pending)")
        
        if not silent and not self.silent_messages then
            UIManager:show(InfoMessage:new{
                text = T(_("Session saved offline (%1 pending)"), #self.pending_sessions),
                timeout = 3,
            })
        end
        
        return false
    end
end

function BookloreSync:syncHistoricalData()
    local function startSync()
        self.historical_sync_ack = true
        self.settings:saveSetting("historical_sync_ack", self.historical_sync_ack)
        self.settings:flush()
        self:_runHistoricalDataSync()
    end

    if not self.historical_sync_ack then
        UIManager:show(ConfirmBox:new{
            text = _("This should only be run once. Any run after this will cause sessions to show up multiple times in booklore"),
            ok_text = _("Sync now"),
            cancel_text = _("Cancel"),
            ok_callback = function()
                startSync()
            end,
        })
        return
    end

    UIManager:show(ConfirmBox:new{
        text = _("You already synced historical data. Are you sure you want to sync again and possibly create duplicate entries?"),
        ok_text = _("Sync again"),
        cancel_text = _("Cancel"),
        ok_callback = function()
            startSync()
        end,
    })
end

function BookloreSync:_runHistoricalDataSync()
    UIManager:show(InfoMessage:new{
        text = _("Scanning KOReader statistics database..."),
        timeout = 2,
    })
    
    logger.info("BookloreSync: Starting historical data sync")
    
    local statistics_db_path = DataStorage:getDataDir() .. "/settings/statistics.sqlite3"
    logger.info("BookloreSync: Statistics DB path:", statistics_db_path)
    
    -- Check if database exists
    local file = io.open(statistics_db_path, "r")
    if not file then
        UIManager:show(InfoMessage:new{
            text = _("Statistics database not found"),
            timeout = 3,
        })
        logger.warn("BookloreSync: Statistics database not found at:", statistics_db_path)
        return
    end
    file:close()
    
    -- Open database using lua-ljsqlite3
    local conn = SQ3.open(statistics_db_path)
    if not conn then
        UIManager:show(InfoMessage:new{
            text = _("Failed to open statistics database"),
            timeout = 3,
        })
        logger.warn("BookloreSync: Failed to open database")
        return
    end
    
    -- Query page_stat to get all reading history, ordered by book and time
    local sql_stmt = [[
        SELECT 
            book.md5,
            book.id,
            page_stat.start_time,
            page_stat.duration,
            page_stat.page,
            book.pages
        FROM page_stat
        JOIN book ON page_stat.id_book = book.id
        WHERE page_stat.duration > 0
        ORDER BY book.id, page_stat.start_time ASC
    ]]
    
    logger.info("BookloreSync: Executing database query")
    
    -- Execute query and get results
    local stmt = conn:prepare(sql_stmt)
    if not stmt then
        UIManager:show(InfoMessage:new{
            text = _("Failed to prepare database query"),
            timeout = 3,
        })
        conn:close()
        return
    end
    
    -- Parse all page records from database
    local page_records = {}
    local parse_errors = 0
    
    for row in stmt:rows() do
        local book_hash = row[1]
        local book_id = tonumber(row[2])
        local start_time = tonumber(row[3])
        local duration = tonumber(row[4])
        local page = tonumber(row[5]) or 0
        local total_pages = tonumber(row[6]) or 0
        
        if book_hash and start_time and duration and book_id then
            local page_record = {
                book_hash = book_hash,
                book_id = book_id,
                start_time = start_time,
                duration = duration,
                page = page,
                total_pages = total_pages
            }
            table.insert(page_records, page_record)
        else
            parse_errors = parse_errors + 1
            logger.warn("BookloreSync: Invalid record - missing required fields")
        end
    end
    
    stmt:close()
    conn:close()
    
    logger.info("BookloreSync: Parsed", #page_records, "page records,", parse_errors, "parse errors")
    
    if #page_records == 0 then
        UIManager:show(InfoMessage:new{
            text = _("No valid page records found"),
            timeout = 3,
        })
        return
    end
    
    -- Group pages into sessions using statistics.koplugin approach
    -- Process records in order, grouping by book and temporal continuity
    local sessions = {}
    local current_session = nil
    local session_timeout = 300  -- 5 minutes gap ends a session (same as statistics plugin)
    
    for i, record in ipairs(page_records) do
        local should_start_new_session = false
        
        if not current_session then
            -- First record, start new session
            should_start_new_session = true
        elseif record.book_hash ~= current_session.book_hash then
            -- Different book, end current session and start new one
            table.insert(sessions, current_session)
            should_start_new_session = true
        else
            -- Same book - check if page is within reading window
            local last_page = current_session.pages[#current_session.pages]
            local time_gap = record.start_time - (last_page.start_time + last_page.duration)
            
            if time_gap > session_timeout then
                -- Gap too large, end current session and start new one
                table.insert(sessions, current_session)
                should_start_new_session = true
            end
        end
        
        if should_start_new_session then
            current_session = {
                book_hash = record.book_hash,
                pages = {record},
                total_pages = record.total_pages
            }
        else
            -- Add to current session
            table.insert(current_session.pages, record)
        end
    end
    
    -- Don't forget the last session
    if current_session then
        table.insert(sessions, current_session)
    end
    
    logger.info("BookloreSync: Grouped", #page_records, "page records into", #sessions, "sessions")
    
    UIManager:show(InfoMessage:new{
        text = T(_("Found %1 sessions, syncing..."), #sessions),
        timeout = 2,
    })
    
    local sessions_synced = 0
    local sessions_skipped = 0
    local sessions_failed = 0
    
    -- Process each session
    for session_num, session in ipairs(sessions) do
        local book_hash = session.book_hash
        local pages = session.pages
        
        -- Calculate session metrics from pages
        local first_page = pages[1]
        local last_page = pages[#pages]
        
        local start_time = first_page.start_time
        local end_time = last_page.start_time + last_page.duration
        local duration_seconds = end_time - start_time
        
        local start_page = first_page.page
        local end_page = last_page.page
        
        logger.info("BookloreSync: Session", session_num, "/", #sessions, "- Hash:", book_hash, "Pages:", #pages, "Duration:", duration_seconds, "s")
        
        -- Check cache first, then server if needed
        local book_id = nil
        if self.book_cache.book_ids then
            book_id = self.book_cache.book_ids[book_hash]
        end
        if not book_id then
            book_id = self:getBookIdByHash(book_hash)
            if book_id then
                if not self.book_cache.book_ids then
                    self.book_cache.book_ids = {}
                end
                self.book_cache.book_ids[book_hash] = book_id
                self.local_db:saveSetting("book_cache", self.book_cache)
                self.local_db:flush()
            end
        end
        
        if book_id then
            logger.info("BookloreSync: Book found in Booklore, ID:", book_id)
            
            -- Calculate progress using page count from statistics DB when available
            local total_pages = session.total_pages or 0
            local start_progress = 0.0
            local end_progress = 0.0
            if total_pages > 0 then
                start_progress = self:roundProgress(math.min((start_page / total_pages) * 100, 100.0))
                end_progress = self:roundProgress(math.min((end_page / total_pages) * 100, 100.0))
            else
                -- Fallback heuristic: use relative page movement
                if end_page > start_page then
                    end_progress = math.min(end_page - start_page, 100.0)
                end
            end
            
            -- Create session data
            local session_data = {
                bookId = book_id,
                bookType = "EPUB",
                startTime = self:formatTimestamp(start_time),
                endTime = self:formatTimestamp(end_time),
                durationSeconds = duration_seconds,
                startProgress = start_progress,
                endProgress = end_progress,
                progressDelta = self:roundProgress(end_progress - start_progress),
                startLocation = tostring(start_page),
                endLocation = tostring(end_page),
            }
            
            logger.info("BookloreSync: Syncing session #", session_num, "- Book ID:", book_id, "Duration:", duration_seconds, "s, Pages:", start_page, "to", end_page)
            logger.info("BookloreSync: Session times - Start:", session_data.startTime, "End:", session_data.endTime)
            
            -- Send to server with detailed result tracking
            local success, error_msg = self:sendHistoricalSession(session_data)
            if success then
                sessions_synced = sessions_synced + 1
                logger.info("BookloreSync: ✓ Session synced successfully (#", sessions_synced, "/", #sessions, ")")
            else
                sessions_failed = sessions_failed + 1
                logger.warn("BookloreSync: ✗ Session sync failed (#", sessions_failed, "):", error_msg or "unknown error")
            end
            
            -- Rate limiting: sleep 1 second between requests to avoid hammering the endpoint
            -- os.execute("sleep 1")
        else
            logger.info("BookloreSync: Book not found in Booklore, skipping")
            sessions_skipped = sessions_skipped + 1
        end
        
        -- Progress update every 10 sessions
        if session_num % 10 == 0 then
            UIManager:show(InfoMessage:new{
                text = T(_("Synced %1/%2 sessions..."), session_num, #sessions),
                timeout = 1,
            })
        end
    end
    
    logger.info("BookloreSync: Historical sync complete - total sessions:", #sessions, "synced:", sessions_synced, "skipped:", sessions_skipped, "failed:", sessions_failed)
    
    local result_text
    if sessions_failed > 0 then
        result_text = T(_("Total sessions: %1\nSynced: %2\nSkipped: %3\nFailed: %4"), #sessions, sessions_synced, sessions_skipped, sessions_failed)
    else
        result_text = T(_("Total sessions: %1\nSynced: %2\nSkipped: %3"), #sessions, sessions_synced, sessions_skipped)
    end
    
    UIManager:show(InfoMessage:new{
        text = result_text,
        timeout = 5,
    })
end

function BookloreSync:sendHistoricalSession(session_data)
    -- Enhanced version with detailed logging for debugging historical sync issues
    local url = self.server_url
    if url:sub(-1) == "/" then
        url = url:sub(1, -2)
    end
    url = url .. "/api/v1/reading-sessions"
    
    local md5 = require("ffi/sha2").md5
    local password_hash = md5(self.password)
    
    local http = require("socket.http")
    local ltn12 = require("ltn12")
    local json = require("json")
    
    http.TIMEOUT = 10  -- Longer timeout to ensure request completes
    
    local json_data = json.encode(session_data)
    logger.info("BookloreSync: POST to", url)
    logger.info("BookloreSync: Payload:", json_data)
    
    local response_body = {}
    
    local res, code, response_headers = http.request{
        url = url,
        method = "POST",
        sink = ltn12.sink.table(response_body),
        source = ltn12.source.string(json_data),
        headers = {
            ["x-auth-user"] = self.username,
            ["x-auth-key"] = password_hash,
            ["Content-Type"] = "application/json",
            ["Content-Length"] = tostring(#json_data),
        },
    }
    
    local response_text = table.concat(response_body)
    logger.info("BookloreSync: Response code:", tostring(code))
    
    if response_text and response_text ~= "" then
        logger.info("BookloreSync: Response body:", response_text)
    else
        logger.info("BookloreSync: Empty response body")
    end
    
    if code == 202 or code == 200 then
        logger.info("BookloreSync: ✓ Historical session synced successfully (code", code, ")")
        return true, nil
    else
        local error_msg = "HTTP " .. tostring(code)
        if response_text and response_text ~= "" then
            error_msg = error_msg .. ": " .. response_text
        end
        logger.warn("BookloreSync: ✗ Historical session failed -", error_msg)
        return false, error_msg
    end
end

function BookloreSync:syncPendingSessions(silent)
    silent = silent or false
    if #self.pending_sessions == 0 then
        logger.info("BookloreSync: No pending sessions to sync")
        if not silent then
            UIManager:show(InfoMessage:new{
                text = _("No pending sessions to sync"),
                timeout = 2,
            })
        end
        return
    end
    
    logger.info("BookloreSync: Syncing", #self.pending_sessions, "pending sessions")
    
    if not silent then
        UIManager:show(InfoMessage:new{
            text = T(_("Syncing %1 pending sessions..."), #self.pending_sessions),
            timeout = 2,
        })
    end
    
    local http = require("socket.http")
    local ltn12 = require("ltn12")
    local json = require("json")
    local md5 = require("ffi/sha2").md5
    
    http.TIMEOUT = 10
    
    local url = self.server_url
    if url:sub(-1) == "/" then
        url = url:sub(1, -2)
    end
    url = url .. "/api/v1/reading-sessions"
    
    local password_hash = md5(self.password)
    
    local synced_count = 0
    local failed_sessions = {}
    
    for i, session_data in ipairs(self.pending_sessions) do
        logger.info("BookloreSync: Syncing pending session", i, "of", #self.pending_sessions)
        
        -- If session has hash but no bookId, try to resolve it now
        if session_data.bookHash and not session_data.bookId then
            logger.info("BookloreSync: Resolving book ID for hash:", session_data.bookHash)
            local book_id = self:getBookIdByHash(session_data.bookHash)
            if book_id then
                session_data.bookId = book_id
                logger.info("BookloreSync: Resolved book ID:", book_id)
            else
                logger.warn("BookloreSync: Failed to resolve book ID, will retry later")
                table.insert(failed_sessions, session_data)
                -- Skip to next session
                goto continue
            end
        end
        
        -- Remove bookHash from payload (server doesn't need it)
        local send_data = {
            bookId = session_data.bookId,
            bookType = session_data.bookType,
            startTime = session_data.startTime,
            endTime = session_data.endTime,
            durationSeconds = session_data.durationSeconds,
            startProgress = session_data.startProgress,
            endProgress = session_data.endProgress,
            progressDelta = session_data.progressDelta,
            startLocation = session_data.startLocation,
            endLocation = session_data.endLocation,
        }
        
        local json_data = json.encode(send_data)
        local response_body = {}
        
        local res, code, response_headers = http.request{
            url = url,
            method = "POST",
            sink = ltn12.sink.table(response_body),
            source = ltn12.source.string(json_data),
            headers = {
                ["x-auth-user"] = self.username,
                ["x-auth-key"] = password_hash,
                ["Content-Type"] = "application/json",
                ["Content-Length"] = tostring(#json_data),
            },
        }
        
        if code == 202 or code == 200 then
            synced_count = synced_count + 1
            logger.info("BookloreSync: Session", i, "synced successfully")
        else
            logger.warn("BookloreSync: Session", i, "failed to sync, code:", tostring(code))
            table.insert(failed_sessions, session_data)
        end
        
        ::continue::
    end
    
    -- Update pending sessions with only the failed ones
    self.pending_sessions = failed_sessions
    self.local_db:saveSetting("pending_sessions", self.pending_sessions)
    self.local_db:flush()
    
    logger.info("BookloreSync: Sync complete -", synced_count, "synced,", #failed_sessions, "failed")
    
    if not silent then
        if synced_count > 0 then
            local message
            if #failed_sessions > 0 then
                message = T(_("%1 synced, %2 failed"), synced_count, #failed_sessions)
            else
                message = T(_("All %1 sessions synced successfully!"), synced_count)
            end
            
            UIManager:show(InfoMessage:new{
                text = message,
                timeout = 3,
            })
        else
            UIManager:show(InfoMessage:new{
                text = _("All sync attempts failed - check connection"),
                timeout = 3,
            })
        end
    end
end

return BookloreSync
