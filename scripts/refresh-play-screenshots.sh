#!/usr/bin/env bash
# Re-renders PlayStorePreviews and copies the resulting PNGs into the
# Play Console listing under
# composeApp/src/main/play/listings/en-GB/graphics.
#
# Source of truth:
# composeApp/src/main/kotlin/ee/schimke/cadence/preview/playstore/PlayStorePreviews.kt
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

./gradlew :composeApp:renderPreviews

RENDERS="composeApp/build/compose-previews/renders"
GRAPHICS="composeApp/src/main/play/listings/en-GB/graphics"

copy() {
  local src="$RENDERS/$1"
  local dst="$GRAPHICS/$2"
  if [ ! -f "$src" ]; then
    echo "missing render: $src" >&2
    exit 1
  fi
  install -m 0644 "$src" "$dst"
  echo "$dst"
}

copy playstore.PlayStorePreviewsKt.PlayStorePhoneHomeLight_Play_Store_phone_Home_light.png       phone-screenshots/01-home-light.png
copy playstore.PlayStorePreviewsKt.PlayStorePhoneHomeDark_Play_Store_phone_Home_dark.png         phone-screenshots/02-home-dark.png
copy playstore.PlayStorePreviewsKt.PlayStorePhoneSync_Play_Store_phone_Sync.png                  phone-screenshots/03-sync.png
copy playstore.PlayStorePreviewsKt.PlayStorePhoneBluetooth_Play_Store_phone_Bluetooth.png        phone-screenshots/04-bluetooth.png
copy playstore.PlayStorePreviewsKt.PlayStorePhoneManage_Play_Store_phone_Manage.png              phone-screenshots/05-manage.png
copy playstore.PlayStorePreviewsKt.PlayStoreSevenInchHomeLight_Play_Store_7_tablet_Home_light.png seven-inch-screenshots/01-home-light.png
copy playstore.PlayStorePreviewsKt.PlayStoreSevenInchHomeDark_Play_Store_7_tablet_Home_dark.png   seven-inch-screenshots/02-home-dark.png
copy playstore.PlayStorePreviewsKt.PlayStoreTenInchHomeLight_Play_Store_10_tablet_Home_light.png  ten-inch-screenshots/01-home-light.png
copy playstore.PlayStorePreviewsKt.PlayStoreTenInchHomeDark_Play_Store_10_tablet_Home_dark.png    ten-inch-screenshots/02-home-dark.png
