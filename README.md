# DUALITY: Escape Together

Unity 6 mobile 3D co-op puzzle prototype for two players.

## Gameplay
- First-person 3D rooms with metallic surfaces, accent lighting, emissive surfaces, and a sci-fi puzzle console.
- Three chambers. Enter the sequence 2 -> 4 -> 1, then both players must stand on different coloured pressure plates to open the bulkhead.
- Red host and blue joining player.
- Local Wi-Fi UDP lobby on port 47822. Both phones must be on the same Wi-Fi network. The host creates a lobby; the second phone enters the host phone's local IPv4 address.
- Touch controls on mobile; WASD, mouse look and E on desktop.

## Android APK build
The repository contains a Unity 6 project and GitHub Actions workflow. On every project push, Actions attempts to build an APK and upload it as the DUALITY-APK artifact.

Unity licensing is required by Unity's build tools. The previous Actions failure was specifically "Missing Unity License File and no Serial was found"; it was not a source compilation error. To allow CI to build, configure a Unity Personal license file as repository secret UNITY_LICENSE (recommended), or configure the Unity serial/credentials secrets supported by GameCI. Do not commit a license file or password into this repository.

1. Open repository Settings -> Secrets and variables -> Actions.
2. Add UNITY_LICENSE using your Unity Personal license activation file contents.
3. Open Actions -> Build DUALITY Unity Android APK -> Run workflow (or push a change).
4. Download DUALITY-APK from a successful run and install the APK on Android.

## Notes
This is an early playable foundation, not a finished commercial game. Test local Wi-Fi play on two physical Android devices; routers with client isolation enabled can block device-to-device traffic. The prototype uses generated meshes and Unity's built-in Standard shader so it does not depend on paid assets.
