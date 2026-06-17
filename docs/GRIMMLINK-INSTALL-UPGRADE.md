# GrimmLink Install and Upgrade

## Clean Install

1. Deploy the Grimmory fork server (see image tags below).
2. Run the server — Flyway migrations apply automatically from `V1__` through the latest `V900x`.
3. Create a user account via the Web UI.
4. Install the [GrimmLink KOReader plugin](https://github.com/0xstillb/GrimmLink).
5. Configure the plugin:

   | Setting | Value |
   | :------ | :---- |
   | Server URL | `https://your-server.example.com` |
   | Username | Your Grimmory username |
   | Auth Key | Generate from user settings in the Web UI |

## Upgrade from Existing Grimmory Fork

1. Pull the new server image.
2. Restart the server — new Flyway migrations (`V9001+`) run automatically.
3. Verify the Web UI loads and login works.
4. Confirm KOReader plugin can still authenticate and sync.
5. Re-auth is **not** required unless the auth key was rotated.

## Upgrade from Upstream Grimmory

1. Pull the fork image (not the upstream image).
2. The fork's `V9001+` migrations will be applied on top of your existing upstream schema.
3. Verify data integrity and login.
4. Install the GrimmLink plugin if not already present.

## Docker / Image Tags

| Tag | Channel | Use |
| :-- | :------ | :-- |
| `latest` | Stable | Production deployments |
| `develop` | Nightly/test | Testing latest changes |
| `vX.Y.Z` | Pinned | Rollback and reproducible testing |

> **Note:** Replace `X.Y.Z` with the actual release version when tagging.

## Post-Upgrade Smoke Test

1. **Log in** to the Web UI with your existing credentials.
2. **Sync progress** from KOReader — push reading progress and confirm the server stores it.
3. **Check Web UI** — verify the reading percentage appears on the book detail page.
4. **Push/pull metadata** — push a rating or bookmark from KOReader, then pull on another device or session; verify the data arrives.
5. **Restart the server** — confirm all progress, shelves, and metadata persist after restart.
