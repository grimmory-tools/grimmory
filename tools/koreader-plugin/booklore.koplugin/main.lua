--[[--
Booklore KOReader Plugin

Syncs reading sessions to Booklore server via REST API.

@module koplugin.BookloreSync
--]]--

local DataStorage = require("datastorage")
local Dispatcher = require("dispatcher")
local InfoMessage = require("ui/widget/infomessage")
local InputDialog = require("ui/widget/inputdialog")
local LuaSettings = require("luasettings")
local UIManager = require("ui/uimanager")
local WidgetContainer = require("ui/widget/container/widgetcontainer")
local logger = require("logger")
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
    self.min_duration = self.settings:readSetting("min_duration") or 5
    
    -- Persistent local database for offline support
    self.local_db = LuaSettings:open(DataStorage:getSettingsDir() .. "/booklore_db.lua")
    
    -- Cache for book hash -> bookId mapping (persisted)
    self.book_cache = self.local_db:readSetting("book_cache") or {}
    
    -- Current reading session tracking
    self.current_session = nil
    
    -- Pending sessions queue (for offline sync)
    self.pending_sessions = self.local_db:readSetting("pending_sessions") or {}
    
    -- Register menu
    self.ui.menu:registerToMainMenu(self)
end

function BookloreSync:addToMainMenu(menu_items)
    menu_items.booklore_sync = {
        text = _("Booklore Sync"),
        sorting_hint = "more_tools",
        sub_item_table = {
            {
                text = _("Enable Sync"),
                checked_func = function()
                    return self.is_enabled
                end,
                callback = function()
                    self.is_enabled = not self.is_enabled
                    self.settings:saveSetting("is_enabled", self.is_enabled)
                    self.settings:flush()
                    UIManager:show(InfoMessage:new{
                        text = self.is_enabled and _("Booklore sync enabled") or _("Booklore sync disabled"),
                        timeout = 2,
                    })
                end,
            },
            {
                text = _("Server URL"),
                keep_menu_open = true,
                callback = function()
                    self:configureServerUrl()
                end,
            },
            {
                text = _("Username"),
                keep_menu_open = true,
                callback = function()
                    self:configureUsername()
                end,
            },
            {
                text = _("Password"),
                keep_menu_open = true,
                callback = function()
                    self:configurePassword()
                end,
            },
            {
                text = _("Test Connection"),
                enabled_func = function()
                    return self.server_url ~= "" and self.username ~= ""
                end,
                callback = function()
                    self:testConnection()
                end,
            },
            {
                text = _("Minimum Session Duration"),
                keep_menu_open = true,
                callback = function()
                    self:configureMinDuration()
                end,
            },
            {
                text = _("Sync Pending Sessions"),
                enabled_func = function()
                    return #self.pending_sessions > 0
                end,
                callback = function()
                    self:syncPendingSessions()
                end,
            },
            {
                text = _("Clear Pending Sessions"),
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
                callback = function()
                    UIManager:show(InfoMessage:new{
                        text = T(_("%1 sessions pending sync"), #self.pending_sessions),
                        timeout = 2,
                    })
                end,
            },
            {                text = _("View Cache Status"),
                callback = function()
                    local cache_count = 0
                    for _ in pairs(self.book_cache) do
                        cache_count = cache_count + 1
                    end
                    UIManager:show(InfoMessage:new{
                        text = T(_("Cached books: %1\nPending sessions: %2"), cache_count, #self.pending_sessions),
                        timeout = 3,
                    })
                end,
            },
            {
                text = _("Clear Local Cache"),
                enabled_func = function()
                    local count = 0
                    for _ in pairs(self.book_cache) do
                        count = count + 1
                    end
                    return count > 0
                end,
                callback = function()
                    self.book_cache = {}
                    self.local_db:saveSetting("book_cache", self.book_cache)
                    self.local_db:flush()
                    UIManager:show(InfoMessage:new{
                        text = _("Local book cache cleared"),
                        timeout = 2,
                    })
                end,
            },
            {                text = _("───────────────"),
                separator = true,
            },
            {
                text = _("🧪 Sync Historical Data"),
                enabled_func = function()
                    return self.server_url ~= "" and self.username ~= "" and self.is_enabled
                end,
                callback = function()
                    self:syncHistoricalData()
                end,
            },
        },
    }
end

function BookloreSync:configureServerUrl()
    local input_dialog
    input_dialog = InputDialog:new{
        title = _("Booklore Server URL"),
        input = self.server_url,
        input_hint = "http://192.168.1.100:6060",
        buttons = {
            {
                {
                    text = _("Cancel"),
                    callback = function()
                        UIManager:close(input_dialog)
                    end,
                },
                {
                    text = _("Save"),
                    is_enter_default = true,
                    callback = function()
                        self.server_url = input_dialog:getInputText()
                        self.settings:saveSetting("server_url", self.server_url)
                        self.settings:flush()
                        UIManager:close(input_dialog)
                        UIManager:show(InfoMessage:new{
                            text = _("Server URL saved"),
                            timeout = 1,
                        })
                    end,
                },
            },
        },
    }
    UIManager:show(input_dialog)
    input_dialog:onShowKeyboard()
end

function BookloreSync:configureUsername()
    local input_dialog
    input_dialog = InputDialog:new{
        title = _("Booklore Username"),
        input = self.username,
        buttons = {
            {
                {
                    text = _("Cancel"),
                    callback = function()
                        UIManager:close(input_dialog)
                    end,
                },
                {
                    text = _("Save"),
                    is_enter_default = true,
                    callback = function()
                        self.username = input_dialog:getInputText()
                        self.settings:saveSetting("username", self.username)
                        self.settings:flush()
                        UIManager:close(input_dialog)
                        UIManager:show(InfoMessage:new{
                            text = _("Username saved"),
                            timeout = 1,
                        })
                    end,
                },
            },
        },
    }
    UIManager:show(input_dialog)
    input_dialog:onShowKeyboard()
end

function BookloreSync:configureMinDuration()
    local input_dialog
    input_dialog = InputDialog:new{
        title = _("Minimum Session Duration (seconds)"),
        input = tostring(self.min_duration),
        input_hint = "5",
        buttons = {
            {
                {
                    text = _("Cancel"),
                    callback = function()
                        UIManager:close(input_dialog)
                    end,
                },
                {
                    text = _("Save"),
                    is_enter_default = true,
                    callback = function()
                        local input_value = tonumber(input_dialog:getInputText())
                        if input_value and input_value > 0 then
                            self.min_duration = input_value
                            self.settings:saveSetting("min_duration", self.min_duration)
                            self.settings:flush()
                            UIManager:close(input_dialog)
                            UIManager:show(InfoMessage:new{
                                text = T(_("Minimum duration set to %1 seconds"), tostring(self.min_duration)),
                                timeout = 2,
                            })
                        else
                            UIManager:show(InfoMessage:new{
                                text = _("Please enter a valid number greater than 0"),
                                timeout = 2,
                            })
                        end
                    end,
                },
            },
        },
    }
    UIManager:show(input_dialog)
    input_dialog:onShowKeyboard()
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
    
    -- Try to sync pending sessions when reader becomes ready (device came online)
    if #self.pending_sessions > 0 then
        logger.info("BookloreSync: Attempting to sync pending sessions on reader ready")
        self:syncPendingSessions()
    end
    
    self:startSession()
end

function BookloreSync:onCloseDocument()
    if not self.is_enabled then
        return
    end
    
    logger.info("BookloreSync: Document closing, ending session")
    self:endSession()
end

function BookloreSync:onSuspend()
    if not self.is_enabled then
        return
    end
    
    logger.info("BookloreSync: Device suspending, ending session")
    self:endSession()
end

function BookloreSync:onResume()
    if not self.is_enabled or self.server_url == "" then
        return
    end
    
    logger.info("BookloreSync: Device resuming from sleep")
    
    -- Try to sync pending sessions when device wakes up (might have network now)
    if #self.pending_sessions > 0 then
        logger.info("BookloreSync: Attempting to sync pending sessions on resume")
        self:syncPendingSessions()
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
    
    -- Calculate MD5 hash of the book file
    local book_hash = self:calculateBookHash(file_path)
    if not book_hash then
        logger.warn("BookloreSync: Failed to calculate book hash")
        return
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
            start_progress = current_page / total_pages
            start_location = tostring(current_page)
        elseif self.ui.rolling then
            -- EPUB or reflowable format
            local cur_page = self.ui.document:getCurrentPage()
            local total_pages = self.ui.document:getPageCount()
            start_progress = cur_page / total_pages
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
    if self.book_cache[book_hash] then
        logger.info("BookloreSync: Found book ID in local cache:", tostring(self.book_cache[book_hash]))
        return self.book_cache[book_hash]
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
        self.book_cache[book_hash] = book_data.id
        self.local_db:saveSetting("book_cache", self.book_cache)
        self.local_db:flush()
        logger.info("BookloreSync: Cached book ID for offline use")
        
        return book_data.id
    end
    
    logger.warn("BookloreSync: No book ID in response")
    return nil
end

function BookloreSync:endSession()
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
            end_progress = current_page / total_pages
            end_location = tostring(current_page)
        elseif self.ui.rolling then
            -- EPUB or reflowable format
            local cur_page = self.ui.document:getCurrentPage()
            local total_pages = self.ui.document:getPageCount()
            end_progress = cur_page / total_pages
            end_location = tostring(cur_page)
        end
    end
    
    local end_time = os.time()
    local duration_seconds = end_time - self.current_session.start_time
    
    -- Don't record sessions shorter than minimum duration (likely false triggers)
    if duration_seconds < self.min_duration then
        logger.info("BookloreSync: Session too short, not recording (", duration_seconds, "s, minimum is ", self.min_duration, "s)")
        self.current_session = nil
        return
    end
    
    local progress_delta = end_progress - self.current_session.start_progress
    
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
    
    -- If we don't have a book ID, queue immediately for later resolution
    if not self.current_session.book_id then
        logger.info("BookloreSync: No book ID - queuing session for offline sync")
        table.insert(self.pending_sessions, session_data)
        self.local_db:saveSetting("pending_sessions", self.pending_sessions)
        self.local_db:flush()
        UIManager:show(InfoMessage:new{
            text = T(_("Session saved offline (%1 pending)"), #self.pending_sessions),
            timeout = 3,
        })
        self.current_session = nil
        return
    end
    
    -- Send session to server
    self:sendSessionToServer(session_data)
    
    -- Clear current session
    self.current_session = nil
end

function BookloreSync:formatTimestamp(unix_time)
    -- Convert Unix timestamp to ISO 8601 format for Java Instant
    -- Format: YYYY-MM-DDTHH:MM:SSZ
    return os.date("!%Y-%m-%dT%H:%M:%SZ", unix_time)
end

function BookloreSync:sendSessionToServer(session_data)
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
        UIManager:show(InfoMessage:new{
            text = _("Reading session synced to Booklore"),
            timeout = 2,
        })
        
        -- If we have pending sessions, try to sync them now
        if #self.pending_sessions > 0 then
            logger.info("BookloreSync: Successful sync detected, attempting to sync pending sessions")
            self:syncPendingSessions()
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
        
        UIManager:show(InfoMessage:new{
            text = T(_("Session saved offline (%1 pending)"), #self.pending_sessions),
            timeout = 3,
        })
        
        return false
    end
end

function BookloreSync:syncHistoricalData()
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
    
    -- Query page_stat to get all reading history, ordered by book and time
    local query = [[
SELECT 
    book.md5,
    book.id,
    page_stat.start_time,
    page_stat.duration,
    page_stat.page
FROM page_stat
JOIN book ON page_stat.id_book = book.id
WHERE page_stat.duration > 0
ORDER BY book.id, page_stat.start_time ASC
]]
    
    -- Create a temporary SQL file
    local tmp_sql = "/tmp/booklore_query.sql"
    local sql_file = io.open(tmp_sql, "w")
    if not sql_file then
        UIManager:show(InfoMessage:new{
            text = _("Failed to create query file"),
            timeout = 3,
        })
        return
    end
    sql_file:write(query)
    sql_file:close()
    
    -- Execute sqlite3 command and capture output
    local cmd = string.format('sqlite3 -separator "|" "%s" < "%s" 2>&1', statistics_db_path, tmp_sql)
    logger.info("BookloreSync: Executing command:", cmd)
    
    local handle = io.popen(cmd)
    if not handle then
        UIManager:show(InfoMessage:new{
            text = _("Failed to execute database query"),
            timeout = 3,
        })
        os.remove(tmp_sql)
        return
    end
    
    local output = handle:read("*a")
    handle:close()
    os.remove(tmp_sql)
    
    if not output or output == "" then
        UIManager:show(InfoMessage:new{
            text = _("No historical data found"),
            timeout = 3,
        })
        logger.warn("BookloreSync: No output from database query")
        return
    end
    
    logger.info("BookloreSync: Query output length:", #output)
    logger.info("BookloreSync: First 500 chars of output:", output:sub(1, 500))
    
    -- Parse all page records first
    local page_records = {}
    local parse_errors = 0
    local lines_processed = 0
    
    for line in output:gmatch("[^\r\n]+") do
        lines_processed = lines_processed + 1
        
        -- Skip empty lines and error lines
        if line ~= "" and not line:match("^Error:") then
            -- Check if line contains pipe separator
            if line:match("|") then
                logger.info("BookloreSync: Parsing line", lines_processed, ":", line)
                
                -- Parse the pipe-separated values - split properly
                local parts = {}
                for part in (line .. "|"):gmatch("([^|]*)%|") do
                    table.insert(parts, part)
                end
                
                logger.info("BookloreSync: Parsed", #parts, "parts from line")
                
                -- Need at least 5 parts: md5, book_id, start_time, duration, page
                if #parts >= 5 and parts[1] ~= "" then
                    local page_record = {
                        book_hash = parts[1],
                        book_id = tonumber(parts[2]),
                        start_time = tonumber(parts[3]),
                        duration = tonumber(parts[4]),
                        page = tonumber(parts[5]) or 0
                    }
                    
                    logger.info("BookloreSync: Record - hash:", page_record.book_hash, "book_id:", page_record.book_id, "start:", page_record.start_time, "dur:", page_record.duration, "page:", page_record.page)
                    
                    if page_record.start_time and page_record.duration and page_record.book_id then
                        table.insert(page_records, page_record)
                        logger.info("BookloreSync: ✓ Valid record added (#", #page_records, ")")
                    else
                        parse_errors = parse_errors + 1
                        logger.warn("BookloreSync: ✗ Invalid record - missing required fields")
                    end
                else
                    parse_errors = parse_errors + 1
                    logger.warn("BookloreSync: ✗ Insufficient parts or empty hash - parts:", #parts)
                end
            end
        end
    end
    
    logger.info("BookloreSync: Processed", lines_processed, "lines, parsed", #page_records, "page records,", parse_errors, "parse errors")
    
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
                pages = {record}
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
        
        -- Check if book exists in Booklore
        local book_id = self:getBookIdByHash(book_hash)
        
        if book_id then
            logger.info("BookloreSync: Book found in Booklore, ID:", book_id)
            
            -- Calculate progress (approximate since we don't have total pages)
            -- Assume progress based on page movement
            local start_progress = 0.1
            local end_progress = 0.1
            
            if end_page > start_page then
                -- Some progress was made
                end_progress = start_progress + ((end_page - start_page) * 0.01)
                end_progress = math.min(end_progress, 1.0)
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
                progressDelta = end_progress - start_progress,
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

function BookloreSync:syncPendingSessions()
    if #self.pending_sessions == 0 then
        logger.info("BookloreSync: No pending sessions to sync")
        UIManager:show(InfoMessage:new{
            text = _("No pending sessions to sync"),
            timeout = 2,
        })
        return
    end
    
    logger.info("BookloreSync: Syncing", #self.pending_sessions, "pending sessions")
    
    UIManager:show(InfoMessage:new{
        text = T(_("Syncing %1 pending sessions..."), #self.pending_sessions),
        timeout = 2,
    })
    
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

return BookloreSync
