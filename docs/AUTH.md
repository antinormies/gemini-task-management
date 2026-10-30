# Google sign-in and accounts

## Flow

1. Web uses Google Identity Services; Android uses Credential Manager with the
   same OAuth Web client as serverClientId.
2. The client sends the Google ID token to POST /api/auth/google.
3. The backend verifies the token signature, issuer, audience, and verified email.
4. The backend upserts an Account keyed by Google subject.
5. Android receives a device bearer token; web receives a signed HttpOnly
   session cookie (SameSite=Lax, Secure on HTTPS, 30 days).
6. Every request is bound to the account in Session.info. All transactional
   rows carry account_id, and ORM reads/writes are tenant-filtered.

## Google Cloud setup

1. Create or choose a Google Cloud project with an OAuth consent screen.
2. Create an OAuth client ID of type Web application:
   - Authorized JavaScript origins:
       https://antaratask.dream-team.works
       http://127.0.0.1:8765   (local development only)
3. Create an OAuth client ID of type Android:
   - Package name: id.ai.antaratask
   - SHA-1: the debug and release signing certificate fingerprints
4. Use the Web client ID for both:
   - server: GTM_GOOGLE_CLIENT_ID
   - Android build property: GTM_GOOGLE_CLIENT_ID
   - Android serverClientId (the app uses the Web client ID)
5. If the consent screen is in Testing, add your Google account as a test user.

The backend also accepts GTM_GOOGLE_ANDROID_CLIENT_ID as an additional
audience, but the Android Credential Manager flow normally returns the Web
client audience.

## Server

Add to backend .env:

    GTM_GOOGLE_CLIENT_ID=<web-client-id>.apps.googleusercontent.com
    GTM_GOOGLE_ANDROID_CLIENT_ID=<optional-android-client-id>
    GTM_SESSION_MAX_AGE_SECONDS=2592000

Restart the backend, then open /login.

## Android

    cd mobile
    ./gradlew :app:assembleDebug --no-daemon \
      -PGTM_GOOGLE_CLIENT_ID=<web-client-id>.apps.googleusercontent.com

The client ID is compiled into BuildConfig.GTM_GOOGLE_CLIENT_ID. Without it,
the login screen shows a configuration message instead of calling Google.
The Android OAuth client still needs the package name and signing SHA-1.

## Existing data

Migration 0002_accounts creates a legacy Account and assigns every existing
project, task, tag, device, AI run, and proposal to it. The first successful
Google login claims that legacy account, so the original single-user data
becomes the first signed-in user's data. Later Google users get empty accounts.

## Removal of auto-pair

The old /api/auth/auto-pair, pairing-code, and pair endpoints are gone. An
updated backend requires the new Android APK and a web login. Do not deploy the
account migration until a Google client ID is configured, otherwise nobody can
sign in.
