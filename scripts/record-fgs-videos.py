#!/usr/bin/env python3
"""Record the Play Console foreground-service evidence videos from the running app.

Each scenario drives the real UI over adb, records it with `screenrecord`, and burns
step captions into a band under the phone frame so a reviewer can follow why the
foreground service runs. See docs/play-fgs-videos.md.
"""
import argparse
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'ee.schimke.cadence'
APK = ROOT / 'composeApp/build/outputs/apk/debug/composeApp-debug.apk'
FEED = 'https://feeds.npr.org/500005/podcast.xml'  # Short public episodes (about 5 minutes).
DEVICE_VIDEO = '/sdcard/cadence-fgs.mp4'


class Device:
    def __init__(self, serial):
        self.serial = serial

    def adb(self, *args, **kwargs):
        return subprocess.run(['adb', '-s', self.serial, *args], check=True, capture_output=True, text=True, **kwargs).stdout

    def shell(self, *args):
        return self.adb('shell', *args)

    def nodes(self):
        xml = self.adb('exec-out', 'uiautomator', 'dump', '/dev/tty')
        for node in re.findall(r'<node [^>]*>', xml):
            attr = lambda name: re.search(f' {name}="([^"]*)"', node).group(1)
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', attr('bounds')))
            yield {'text': attr('text'), 'desc': attr('content-desc'), 'center': ((x1 + x2) // 2, (y1 + y2) // 2)}

    def find(self, pattern, timeout=20):
        """Waits for a node whose text or content description fully matches [pattern]."""
        deadline = time.time() + timeout
        while True:
            for node in self.nodes():
                if re.fullmatch(pattern, node['text']) or re.fullmatch(pattern, node['desc']):
                    return node
            if time.time() > deadline:
                raise TimeoutError(f'Nothing on screen matches {pattern!r}')
            time.sleep(1)

    def tap(self, pattern, timeout=20):
        x, y = self.find(pattern, timeout)['center']
        self.shell('input', 'tap', str(x), str(y))
        time.sleep(1.2)

    def scroll_to(self, pattern, swipes=6):
        for _ in range(swipes):
            try:
                return self.find(pattern, timeout=0)
            except TimeoutError:
                self.shell('input', 'swipe', '540', '1900', '540', '900', '500')
                time.sleep(.8)
        return self.find(pattern, timeout=0)

    def key(self, *codes):
        for code in codes:
            self.shell('input', 'keyevent', str(code))
            time.sleep(.6)

    def launch(self):
        self.shell('am', 'start', '-n', f'{PACKAGE}/.MainActivity')
        time.sleep(2.5)

    def shade(self, seconds):
        self.shell('cmd', 'statusbar', 'expand-notifications')
        time.sleep(seconds)
        self.shell('cmd', 'statusbar', 'collapse')
        time.sleep(1)

    def clear_notifications(self):
        self.shell('cmd', 'statusbar', 'expand-notifications')
        time.sleep(1.5)
        try:
            self.tap('Clear all', timeout=2)
        except TimeoutError:
            pass
        self.shell('cmd', 'statusbar', 'collapse')

    def demo_status_bar(self, on):
        self.shell('settings', 'put', 'global', 'sysui_demo_allowed', '1')
        broadcast = ['am', 'broadcast', '-a', 'com.android.systemui.demo', '-e', 'command']
        if not on:
            self.shell(*broadcast, 'exit')
            return
        self.shell(*broadcast, 'enter')
        self.shell(*broadcast, 'clock', '-e', 'hhmm', '0900')
        self.shell(*broadcast, 'battery', '-e', 'level', '100', '-e', 'plugged', 'false')
        self.shell(*broadcast, 'network', '-e', 'wifi', 'show', '-e', 'level', '4')
        self.shell(*broadcast, 'notifications', '-e', 'visible', 'false')


class Recording:
    """A screen recording with captions timed from when each step starts."""

    def __init__(self, device, name, out):
        self.device, self.name, self.out = device, name, out
        self.captions = []

    def __enter__(self):
        self.device.shell('rm', '-f', DEVICE_VIDEO)
        self.process = subprocess.Popen(
            ['adb', '-s', self.device.serial, 'shell', 'screenrecord', '--bit-rate', '8000000', '--time-limit', '180', DEVICE_VIDEO])
        time.sleep(1)
        self.start = time.time()
        return self

    def caption(self, text):
        print(f'  {self.name}: {text}')
        self.captions.append((time.time() - self.start, text))

    def __exit__(self, *error):
        time.sleep(2)
        self.device.shell('pkill', '-INT', 'screenrecord')
        self.process.wait()
        time.sleep(1.5)  # screenrecord finalises the file after SIGINT.
        end = time.time() - self.start
        raw = self.out / f'{self.name}-raw.mp4'
        self.device.adb('pull', DEVICE_VIDEO, str(raw))
        if error[0] is None:
            burn_captions(raw, self.captions, end, self.out / f'{self.name}.mp4')


def burn_captions(raw, captions, end, output):
    def stamp(seconds):
        ms = int(seconds * 1000)
        return f'{ms // 3600000:02}:{ms // 60000 % 60:02}:{ms // 1000 % 60:02},{ms % 1000:03}'

    srt = raw.with_suffix('.srt')
    bounds = [t for t, _ in captions[1:]] + [end]
    srt.write_text(''.join(
        f'{i}\n{stamp(start)} --> {stamp(stop)}\n{text}\n\n'
        for i, ((start, text), stop) in enumerate(zip(captions, bounds), 1)))
    # The caption band sits under the phone frame, so text never hides the UI it describes.
    style = 'Alignment=2,FontName=Sans,FontSize=7,MarginV=10,MarginL=10,MarginR=10,Outline=0,Shadow=0,PrimaryColour=&H00FFFFFF'
    subprocess.run([
        'ffmpeg', '-y', '-loglevel', 'error', '-i', str(raw),
        '-vf', f"pad=iw:ih+420:0:0:color=0x1b1b1f,subtitles={srt}:force_style='{style}'",
        '-c:v', 'libx264', '-crf', '23', '-preset', 'medium', '-pix_fmt', 'yuv420p', '-an', str(output),
    ], check=True)
    print(f'  wrote {output.relative_to(ROOT)}')


def add_feed(d, episodes, say):
    say('The user adds a podcast feed. Cadence prepares its episodes for offline headphones.')
    d.tap('Podcasts')
    d.tap('Podcast feed')
    while (count := int(re.search(r'\d+', d.find(r'Up to \d+ episodes?')['text']).group())) != episodes:
        d.tap('More' if count < episodes else 'Fewer')
    d.tap(r'Feed URL.*')
    d.shell('input', 'text', FEED)
    d.key(111)  # Hide the keyboard.
    d.tap('Add')
    d.find(r'\d+ to download', timeout=40)


def split_into_parts(d, say):
    say('Audio options: split episodes into 1-minute parts, which re-encodes them to AAC on the phone.')
    d.tap(r'CLOUD SYNC .*|Open podcasts')
    d.scroll_to('Audio options')
    d.tap('Audio options')
    d.scroll_to('0 keeps each episode in one piece')
    d.shell('input', 'swipe', '540', '1700', '540', '1100', '500')  # Bring the whole field on screen.
    time.sleep(1)
    x, y = d.find('Split into parts of .*')['center']  # The field's label sits inside it.
    d.shell('input', 'tap', str(x), str(y))
    time.sleep(1)
    d.key('KEYCODE_MOVE_END', 'KEYCODE_DEL', 'KEYCODE_DEL', 'KEYCODE_DEL')
    d.shell('input', 'text', '1')
    d.find('1', timeout=5)
    time.sleep(1.5)
    d.key(111, 4)
    d.find(r'\d+ to process')


def in_background(d, say, text, seconds):
    """Leaves the app and opens the shade, where the foreground service's notification shows."""
    say(text)
    d.key(3)
    d.shell('cmd', 'statusbar', 'expand-notifications')
    time.sleep(seconds)
    d.shell('cmd', 'statusbar', 'collapse')
    time.sleep(1)


def data_sync(d, out, episodes):
    with Recording(d, 'data-sync', out) as rec:
        d.launch()
        add_feed(d, episodes, rec.caption)
        split_into_parts(d, rec.caption)

        rec.caption('Download: the user taps Download. A dataSync foreground service fetches the episodes.')
        d.tap(r'Download \d+ episodes?')
        time.sleep(2)
        in_background(d, rec.caption, 'The user leaves Cadence. The download continues in the background with a progress notification.', 7)
        d.launch()
        d.find('0 to download', timeout=90)
        time.sleep(1.5)

        rec.caption('Process: the user taps Process. A dataSync foreground service transcodes the episodes into AAC parts.')
        d.tap(r'Process \d+ episodes?')
        time.sleep(2.5)
        in_background(d, rec.caption, 'The user leaves Cadence. Transcoding continues in the background with a progress notification.', 9)
        d.launch()
        d.find('0 to process', timeout=120)
        rec.caption('Every episode is ready. Cadence copies them to the headphones when they are plugged in over USB.')
        time.sleep(4)


def media_playback(d, out, episodes):
    d.launch()
    try:
        d.find(r'Play .* on phone', timeout=5)
    except TimeoutError:
        print('  preparing a downloaded episode first (not recorded)')
        add_feed(d, episodes, lambda _: None)
        d.tap(r'Download \d+ episodes?')
        d.find('0 to download', timeout=90)
    with Recording(d, 'media-playback', out) as rec:
        rec.caption('The user taps Play on a downloaded podcast episode.')
        time.sleep(2)
        d.tap(r'Play .* on phone')
        time.sleep(4)
        in_background(d, rec.caption, 'The user leaves Cadence. A mediaPlayback foreground service keeps the episode playing, with system media controls.', 10)
        rec.caption('Audio keeps playing while the user is in another app.')
        d.shell('am', 'start', '-a', 'android.settings.SETTINGS')
        time.sleep(4)
        rec.caption('Back in Cadence, the player shows the same episode, still playing.')
        d.launch()
        time.sleep(5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('scenario', choices=['data-sync', 'media-playback', 'all'])
    parser.add_argument('--serial', required=True)
    parser.add_argument('--allow-physical-device', action='store_true',
                        help='Record a real phone. Its data is cleared, and other apps\' notifications can appear in the shade.')
    parser.add_argument('--episodes', type=int, default=4)
    parser.add_argument('--out', type=Path, default=ROOT / 'build/play-fgs-videos')
    parser.add_argument('--no-install', action='store_true', help='Keep the installed build instead of installing the debug APK')
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial) and not args.allow_physical_device:
        sys.exit('Use a disposable emulator, or pass --allow-physical-device: Cadence\'s data on the device is cleared')
    args.out.mkdir(parents=True, exist_ok=True)

    d = Device(args.serial)
    if not args.no_install:
        d.adb('install', '-r', '-g', str(APK))
    d.shell('am', 'force-stop', PACKAGE)
    d.shell('pm', 'clear', PACKAGE)
    d.shell('pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
    d.shell('settings', 'put', 'system', 'screen_off_timeout', '600000')
    d.key('KEYCODE_WAKEUP', 'KEYCODE_HOME')
    d.demo_status_bar(True)
    if re.fullmatch(r'emulator-\d+', args.serial):
        d.clear_notifications()  # Keep the emulator's own setup notices out of the shade.
    try:
        if args.scenario in ('data-sync', 'all'):
            data_sync(d, args.out, args.episodes)
        if args.scenario in ('media-playback', 'all'):
            media_playback(d, args.out, args.episodes)
    finally:
        d.shell('am', 'force-stop', PACKAGE)  # Stops playback left running by the scenario.
        d.demo_status_bar(False)


if __name__ == '__main__':
    main()
