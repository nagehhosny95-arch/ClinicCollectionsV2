from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GRADLE = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github" / "workflows" / "build-apk.yml").read_text(encoding="utf-8")


def require(text: str, fragment: str, message: str) -> None:
    if fragment not in text:
        raise AssertionError(message)


require(
    GRADLE,
    "debug {\n            isMinifyEnabled = false\n            if (stableSigningConfig != null)",
    "Debug bridge must use the permanent signing config when GitHub secrets are present.",
)
require(
    WORKFLOW,
    "assembleDebug assembleRelease",
    "Signed job must build both the debuggable migration bridge and the release APK.",
)
require(
    WORKFLOW,
    "name: advance-medical-signed-update-",
    "Signed artifacts need an unmistakable stable-update name.",
)
require(
    WORKFLOW,
    "app/build/outputs/apk/debug/app-debug.apk",
    "Signed artifact must include the debuggable migration bridge APK.",
)
require(
    WORKFLOW,
    "app/build/outputs/apk/release/app-release.apk",
    "Signed artifact must include the final release APK.",
)

print("Stable signing workflow checks passed.")
