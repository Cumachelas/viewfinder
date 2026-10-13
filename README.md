# viewfinder (1.0.0)

Android viewfinder for DPs, directors and anybody else: choose a camera (sensor mode) and a lens and see the exact field of view through the phone camera, with frame guides, crop, anamorphic desqueeze and lens coverage. Stills export in the exact format with a caption and are collected per project.

## Download
Get the APK from [Releases](../../releases). Android 10 or newer; on first install allow "install unknown apps" for the app you open it with.

## Features
- Live viewfinder in a white (or dark) mode; aspect guides in individual colours; crop mode
- Camera database with sensor data
- Lens database with focal lengths, widest aperture and image circle; coverage vignette and warning
- Anamorphic: the lens squeeze sets the FOV, the desqueeze sets the display (mismatches are shown squeezed or stretched)
- Phone camera and zoom chosen automatically for the best resolution; calibration factor per phone camera
- Auto-saving projects (director, DP, location, scene, shot, notes) and a gallery per project
- Exports in `Pictures/viewfinder`, with areas the phone cannot see hatched

Sensor and lens data come from manufacturer specs where available. Please verify critical values before relying on them on set; corrections are welcome as issues.

## License
GPL-3.0, see [LICENSE](LICENSE). The bundled [Inter](https://github.com/rsms/inter) font is under the SIL Open Font License 1.1, see [licenses/inter-ofl.txt](licenses/inter-ofl.txt).
