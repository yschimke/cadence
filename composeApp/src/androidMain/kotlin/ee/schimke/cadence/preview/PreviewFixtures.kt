package ee.schimke.cadence.preview

import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.bluetooth.ConnectedDevice
import ee.schimke.cadence.bluetooth.MediaInfo
import ee.schimke.cadence.bluetooth.WorkingMode
import ee.schimke.cadence.data.Volume
import ee.schimke.cadence.datastore.proto.Bookmark
import ee.schimke.cadence.datastore.proto.Device
import ee.schimke.cadence.datastore.proto.NetworkConstraint
import ee.schimke.cadence.datastore.proto.SourceKind
import ee.schimke.cadence.datastore.proto.SyncPreferences
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.datastore.proto.SyncSource
import ee.schimke.cadence.sync.FileSyncViewModel
import ee.schimke.cadence.sync.RefreshProgress
import ee.schimke.cadence.sync.SourceSuggestion
import ee.schimke.cadence.sync.SyncProgress
import ee.schimke.cadence.usb.UsbDevice
import okio.Path.Companion.toPath

/**
 * Hand-rolled fixtures for `@Preview` composables. Kept off the production DI
 * graph so previews render without hitting USB/Bluetooth/DataStore.
 */
internal object PreviewFixtures {
    val devices: List<Device> = listOf(
        Device(
            id = "dev-1",
            name = "OpenSwim Pro",
            path = "content://com.android.externalstorage.documents/tree/0000-0000%3A",
        ),
        Device(
            id = "dev-2",
            name = "OpenRun Pro 2",
            path = "content://com.android.externalstorage.documents/tree/1111-1111%3A",
        ),
    )

    val usbDevices: List<UsbDevice> = listOf(
        UsbDevice(
            id = 1001,
            name = "/dev/bus/usb/001/005",
            deviceClass = 0,
            vendorId = 0x2FE3,
            manufacturerName = "Shokz",
            productId = 0x0100,
            productName = "OpenSwim Pro",
        ),
        UsbDevice(
            id = 1002,
            name = "/dev/bus/usb/001/006",
            deviceClass = 0,
            vendorId = 0x18D1,
            manufacturerName = "Generic",
            productId = 0x4EE7,
            productName = "Android Debug Bridge",
        ),
    )

    val bookmarks: List<Bookmark> = listOf(
        Bookmark(
            name = "Podbean",
            url = "https://www.podbean.com/all",
            favicon = "https://pbcdn1.podbean.com/fs1/site/images/favicon.ico",
        ),
        Bookmark(
            name = "BBC Sounds",
            url = "https://www.bbc.co.uk/sounds",
            favicon = "",
        ),
    )

    val volume: Volume = Volume(
        uuid = "0000-0000",
        volumeName = "SHOKZ",
        state = "mounted",
        description = "Shokz OpenSwim Pro",
        path = "/storage/0000-0000".toPath(),
        removable = true,
    )

    val device: Device = devices.first()
    val rootPath = "/storage/0000-0000".toPath()

    // ---- Bluetooth fixtures ----------------------------------------------

    val connectedDevice = ConnectedDevice(
        name = "OpenSwim Pro",
        address = "A8:F5:E1:7A:60:72",
        profileNames = listOf("A2DP", "HFP"),
        batteryPercent = 78,
        codec = "SBC",
    )

    val nowPlaying = MediaInfo(
        title = "Episode 412 — Tides of the Pacific",
        artist = "Saltwater Daily",
        album = "Saltwater Daily • Spring 2026",
        playing = true,
        durationMs = 47L * 60 * 1000,
        positionMs = 14L * 60 * 1000,
        packageName = "com.podbean.app.podcast",
    )

    val btDisconnected = BluetoothState(
        connectedDevice = null,
        volumePercent = 35,
        maxVolume = 15,
        muted = false,
        mediaInfo = null,
        mediaAccessGranted = false,
        permissionMissing = false,
    )

    val btConnectedPlaying = BluetoothState(
        connectedDevice = connectedDevice,
        volumePercent = 64,
        maxVolume = 15,
        muted = false,
        mediaInfo = nowPlaying,
        mediaAccessGranted = true,
        permissionMissing = false,
    )

    val btPermissionMissing = btDisconnected.copy(permissionMissing = true)

    val btMp3Mode = btConnectedPlaying.copy(
        workingMode = WorkingMode.Mp3,
        mediaInfo = nowPlaying.copy(
            title = "swim/intervals_2k.mp3",
            artist = "On-device flash",
            album = null,
            packageName = null,
        ),
    )

    // ---- Sync fixtures ---------------------------------------------------

    val sources: List<SyncSource> = listOf(
        SyncSource(
            id = "src-podbean",
            name = "Podbean — staged",
            kind = SourceKind.LOCAL_DIRECTORY,
            location = "content://com.android.externalstorage.documents/tree/primary%3APodcasts",
            subpath = "",
        ),
        SyncSource(
            id = "src-drive-swim",
            name = "Drive — Swim Mixes",
            kind = SourceKind.LOCAL_DIRECTORY,
            location = "content://com.google.android.apps.docs.storage/tree/swim-mixes",
            subpath = "",
        ),
        SyncSource(
            id = "src-nas",
            name = "NAS — Audiobooks",
            kind = SourceKind.NFS_SHARE,
            location = "nfs://nas.local/exports/audiobooks",
            subpath = "",
        ),
    )

    val podcastsProfile: SyncProfile = SyncProfile(
        id = "prof-podcasts",
        name = "Morning podcasts",
        source_ids = listOf("src-podbean"),
        staging_subpath = "podcasts",
        refresh_interval_minutes = 60,
        network_constraint = NetworkConstraint.UNMETERED,
        last_refreshed_at = "2026-04-25T07:12:00Z",
        last_error = "",
        auto_refresh = true,
    )

    val swimProfile: SyncProfile = SyncProfile(
        id = "prof-swim",
        name = "Swim mixes",
        source_ids = listOf("src-drive-swim"),
        staging_subpath = "swim",
        refresh_interval_minutes = 240,
        network_constraint = NetworkConstraint.UNMETERED,
        last_refreshed_at = "2026-04-24T06:00:00Z",
        last_error = "",
        auto_refresh = false,
    )

    val audiobooksProfile: SyncProfile = SyncProfile(
        id = "prof-audiobooks",
        name = "Audiobooks",
        source_ids = listOf("src-nas"),
        staging_subpath = "audiobooks",
        refresh_interval_minutes = 720,
        network_constraint = NetworkConstraint.CONNECTED,
        last_refreshed_at = "",
        last_error = "Could not reach nas.local",
        auto_refresh = false,
    )

    val syncPreferences = SyncPreferences(
        target_device_id = "dev-1",
        auto_sync_on_usb = true,
        usb_match = "05E3:0761",
    )

    val refreshIdle = RefreshProgress(running = false)

    val refreshRunning = RefreshProgress(
        running = true,
        profileId = podcastsProfile.id,
        profileName = podcastsProfile.name,
        currentFileName = "412 — Tides of the Pacific.mp3",
        filesCompleted = 3,
        filesTotal = 7,
    )

    val syncIdle = SyncProgress(running = false)

    val syncRunning = SyncProgress(
        running = true,
        currentProfileName = "Morning podcasts",
        currentFileName = "podcasts/412 — Tides of the Pacific.mp3",
        currentBytes = 14L * 1024 * 1024,
        currentTotal = 38L * 1024 * 1024,
        filesCompleted = 5,
        filesTotal = 12,
    )

    val syncDone = SyncProgress(
        running = false,
        filesCompleted = 12,
        filesTotal = 12,
    )

    val suggestions: List<SourceSuggestion> = listOf(
        SourceSuggestion(
            packageName = "com.google.android.apps.docs",
            displayName = "Google Drive",
            description = "Cloud storage exposed via SAF.",
            capabilities = listOf("Cloud"),
            installed = true,
        ),
        SourceSuggestion(
            packageName = "pl.solidexplorer2",
            displayName = "Solid Explorer",
            description = "Adds SMB / FTP / WebDAV to the SAF picker.",
            capabilities = listOf("SMB", "FTP", "WebDAV"),
            installed = false,
        ),
        SourceSuggestion(
            packageName = "io.github.x0b.rcx",
            displayName = "RCX (rclone for Android)",
            description = "Mounts any rclone remote (S3, B2, SFTP, Mega, …).",
            capabilities = listOf("rclone", "S3", "SFTP"),
            installed = false,
        ),
    )

    // Convenience UiState builders for FileSync previews.

    fun fileSyncEmpty(): FileSyncViewModel.UiState = FileSyncViewModel.UiState(
        sources = emptyList(),
        profiles = emptyList(),
        devices = devices,
        preferences = SyncPreferences(),
        refresh = refreshIdle,
        sync = syncIdle,
    )

    fun fileSyncPopulated(): FileSyncViewModel.UiState = FileSyncViewModel.UiState(
        sources = sources,
        profiles = listOf(podcastsProfile, swimProfile, audiobooksProfile),
        devices = devices,
        preferences = syncPreferences,
        refresh = refreshIdle,
        sync = syncIdle,
    )

    fun fileSyncRefreshing(): FileSyncViewModel.UiState =
        fileSyncPopulated().copy(refresh = refreshRunning)

    fun fileSyncSyncing(): FileSyncViewModel.UiState =
        fileSyncPopulated().copy(sync = syncRunning)

    fun fileSyncDone(): FileSyncViewModel.UiState =
        fileSyncPopulated().copy(sync = syncDone)
}
