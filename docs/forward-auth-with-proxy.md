# Forward Auth with Reverse Proxy

Grimmory supports **Forward Auth**, allowing you to specify when a user is logged in using a reverse proxy and existing SSO provider.

## ⚠️ Security

**Important**: Enabling forward auth means Grimmory will **fully trust headers sent by the reverse proxy**. Never expose Grimmory directly to the internet when using forward auth - always route through your authenticated proxy, otherwise outsiders can attempt to impersonate any username they know about.

## Configuration

Provide Grimmory with the following environment variables:

```bash
# Allows Forward Auth
REMOTE_AUTH_ENABLED=true

# Enable automatic user creation (recommended)
REMOTE_AUTH_CREATE_NEW_USERS=true

# Header names (your proxy will specify what header names to use)
REMOTE_AUTH_HEADER_USER=Remote-User        # Username (required)
REMOTE_AUTH_HEADER_NAME=Remote-Name        # Display name
REMOTE_AUTH_HEADER_EMAIL=Remote-Email      # Email address
REMOTE_AUTH_HEADER_GROUPS=Remote-Groups    # Groups/roles

# Admin group name (optional)
REMOTE_AUTH_ADMIN_GROUP=admin              # Specify this if you want a group to automatically get admin rights

# Groups delimiter pattern (optional)
REMOTE_AUTH_GROUPS_DELIMITER=\\s+          # Regex pattern for splitting groups. Default: "\\s+" (whitespace)
                                           # Use "\\s*,\\s*" for comma-separated groups
                                           # Use "\\s*;\\s*" for semicolon-separated groups
```

### Docker Compose Example

```yaml
services:
  grimmory:
    image: ghcr.io/grimmory-tools/grimmory:v0.38.2
    environment:
      # Forward Auth Configuration
      - REMOTE_AUTH_ENABLED=true
      - REMOTE_AUTH_CREATE_NEW_USERS=true
      - REMOTE_AUTH_HEADER_NAME=Remote-Name
      - REMOTE_AUTH_HEADER_USER=Remote-User
      - REMOTE_AUTH_HEADER_EMAIL=Remote-Email
      - REMOTE_AUTH_HEADER_GROUPS=Remote-Groups
      - REMOTE_AUTH_ADMIN_GROUP=admin
      # - REMOTE_AUTH_GROUPS_DELIMITER=\\s*,\\s*  # Uncomment if your proxy sends comma-separated groups
    # ... rest of configuration ...
```

## Setting Up Defaults Permissions

1. **Access Admin Settings**: Log in to Grimmory as an admin user
2. **Navigate to Authentication Settings**: Go to Settings → Authentication
3. **Configure OIDC Auto-Provision** (even if not using OIDC):
   - Enable "Auto User Provisioning". You might need to enter a bogus URL to enable it temporarily.
   - Select the default permissions and libraries for new users.
4. **Save Settings**

## Example: Caddyfile for Authelia Forward Auth

```caddyfile
books.example.com {
  forward_auth authelia:9091 {
    uri /api/authz/forward-auth
    copy_headers Remote-User Remote-Name Remote-Email Remote-Groups
  }

  reverse_proxy grimmory:6060
}
```
