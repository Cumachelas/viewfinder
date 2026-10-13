# viewfinder 1.0

Android viewfinder for DPs and camera operators: choose a cine camera (sensor mode) and a lens and see the exact field of view through the phone camera, with frame guides, crop, anamorphic desqueeze and lens coverage. Stills export in the exact format with a caption and are collected per project.

## Download
Get the APK from [Releases](../../releases). Android 10 or newer; on first install allow "install unknown apps" for the app you open it with.

## Features
- Live viewfinder in a white (or dark) passe-partout; aspect guides in individual colours; crop mode
- Camera database (ARRI, Sony, RED, Kinefinity, Canon, generic formats) with recording area in mm
- Lens database with focal lengths, widest aperture and image circle; coverage vignette and warning
- Anamorphic: the lens squeeze sets the FOV, the desqueeze sets the display (mismatches are shown squeezed or stretched)
- Phone camera and zoom chosen automatically for the best resolution; calibration factor per phone camera
- Auto-saving projects (director, DP, location, scene, shot, notes) and a gallery per project
- Exports in `Pictures/viewfinder`, with areas the phone cannot see hatched

## Geometry
Everything is computed in tan(half angle of view), since a rectilinear image is linear in tangent space.

- Cine: `tan_h = sensor width × lens squeeze / (2 · f)`, `tan_v = sensor height / (2 · f)`.
- Display: horizontal × (desqueeze / lens squeeze). Guides are the largest centred rectangle of the aspect inside it.
- Phone: Camera2 `SENSOR_INFO_PHYSICAL_SIZE` × `ACTIVE_ARRAY_SIZE / PIXEL_ARRAY_SIZE`, cropped centrally to the stream aspect ratio and divided by the zoom ratio, using `LENS_INFO_AVAILABLE_FOCAL_LENGTHS`.
- Coverage: image circle / sensor diagonal (physical, independent of the squeeze).

Code: `optics/Optics.kt`, `camera/PhoneLens.kt`, `ui/FrameOverlay.kt`, `capture/Still.kt`. Data sources are listed in `data/CameraDb.kt` and `data/LensDb.kt`.

## Checks
`gradlew testDebugUnitTest` checks the camera data against published sensor sizes, the consistency of the lens and aspect data, the optics against textbook values, and the phone lens selection.

## Build
`gradlew assembleRelease` builds a signed release using the key in `/keystore` (not in the repository). Without that folder it builds an unsigned release. AGP 9.4, Kotlin 2.4, compileSdk 37, minSdk 29 (Android 10).

Sensor and lens data come from manufacturer specs where available, otherwise rental and retail spec sheets. Please verify critical values before relying on them on set; corrections are welcome as issues.

## License
GPL-3.0, see [LICENSE](LICENSE). The bundled [Inter](https://github.com/rsms/inter) font is under the SIL Open Font License 1.1, see [licenses/inter-ofl.txt](licenses/inter-ofl.txt).
