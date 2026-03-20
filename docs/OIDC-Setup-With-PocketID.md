# OIDC Setup With Pocket ID

## A quick rundown of the technologies

### What is Pocket ID?

Pocket ID is a simple OIDC provider that allows users to authenticate with their passkeys to your services.

The goal of Pocket ID is to be a simple and easy-to-use. There are other self-hosted OIDC providers like Keycloak or ORY Hydra but they are often too complex for simple use cases.

Additionally, what makes Pocket ID special is that it only supports passkey authentication, which means you don’t need a password.

### What is OAuth2?

OAuth2 (Open Authorization 2.0) is an industry-standard protocol for authorization. It allows applications (clients) to gain limited access to user accounts on an HTTP service without sharing the user’s credentials. Instead, it uses access tokens to facilitate secure interactions. OAuth2 is commonly used in scenarios where users need to authenticate via a third-party service.

### What is OpenID Connect (OIDC)?

OIDC (OpenID Connect) is an identity layer built on top of OAuth2. While OAuth2 primarily handles authorization, OIDC adds authentication, enabling applications to verify a user’s identity and obtain profile information. This makes OIDC suitable for SSO solutions, where user identity is central to access management.

## Setting up a client in Pocket ID

### Step 1: Install and Configure Pocket ID

Before setting up the OIDC client, ensure that Pocket ID is installed and running by following the [setup guide](https://github.com/stonith404/pocket-id#setup).

### Step 2: Add a client

Once you have logged in and configured a PassKey you now need to create an OIDC client, this will let Pocket ID know about the application that needs to be configured, and will give you the relevant keys to add to the Grimmory settings dialogue

- Login to PocketID
- Go to OIDC Clients on the left side bar
- Click Add OIDC Client
    - Name: Grimmory [Can be anything you want]
    - Callback URLs: `https://{host}/oauth2-callback` 
- Tick Public & PKCE (This is required for Grimmory to correctly authenticate with the client)
- Copy your Client ID to use later.
- Click show more details and copy the Certificate URL to use later
- Click Save


### Step 3: Configure Grimmory OIDC Settings

To enable OIDC authentication in Grimmory, you need to set the following within the application:

- Navigate to the settings cog at the top of the page
- Provider name - PocketID 
- Client ID - Copied in Step 2 
- Issuer URI - This is the URL where your MAIN page of PocketID is (id.host.com for example)
- JWKS URL - Copied in step 2
- Leave the rest default 
- Click Save

### Step 4: Test the Integration

Once configured, simply click Save Settings and then click the Enabled radio button to activate it. Simply log out and log back in and you should be working with no issues! Any issues please raise an issue on [GitHub](https://github.com/grimmory-tools/grimmory/issues/new?template=bug_report.yml)
