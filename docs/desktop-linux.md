# Cadence desktop on Linux

Every release attaches a `.deb` for Linux x64. On Arch Linux and its derivatives (CachyOS,
EndeavourOS, Manjaro), install the `cadence-desktop-bin` package instead. It repackages that `.deb`
with a menu entry, an icon and a `cadence-desktop` command, and pacman updates it with the rest of
the system.

Its files are named `cadence-desktop`, not `cadence`, because the unrelated KXStudio `cadence`
package already uses those names. The two can be installed together.

## pacman repository (recommended)

Each published release adds its package to the `cadence` repository, which is hosted as assets on
this repository's [`pacman-repo` release](https://github.com/yschimke/cadence/releases/tag/pacman-repo).
Add it to `/etc/pacman.conf` once:

```ini
[cadence]
SigLevel = Optional TrustAll
Server = https://github.com/yschimke/cadence/releases/download/pacman-repo
```

Then:

```bash
sudo pacman -Sy cadence-desktop-bin
```

From then on, `sudo pacman -Syu` installs each new release.

The packages are not GPG-signed yet, so `SigLevel = Optional TrustAll` is needed. What you get is
whatever this repository's release workflow uploaded, fetched over HTTPS from GitHub. That's the
same trust as downloading the `.deb` by hand.

## AUR

`cadence-desktop-bin` is also set up to publish to the AUR for `paru`/`yay` users. It is not live
yet: AUR account registration was paused when this was set up. Once it is,
[`arch-packages.yml`](../.github/workflows/arch-packages.yml) says how to switch it on.

## Build it yourself

```bash
cd packaging/aur/cadence-desktop-bin
makepkg -si
```

This builds the version named in the checked-in PKGBUILD. CI updates `pkgver` for each release
but doesn't commit it.

## Other distributions

Install the `.deb` from the [latest release](https://github.com/yschimke/cadence/releases/latest)
on Debian or Ubuntu (`sudo apt install ./cadence-*-linux-x64.deb`). It doesn't update itself.
