<div style="text-align: center">
    <img src="land-logo.svg" width="100" height="100" alt="LANd Logo">
</div>

# <div style="text-align: center">LANd</div>

LANd is an open-source, cross-platform, wireless file transfer application for Android, iOS, and Desktop (MacOS, Windows, and Linux). 
It is designed to be a simple and secure way to transfer files quickly between any device on the same network.

## Features
* Crossplatform - Android, iOS, and Desktop (MacOS, Windows, and Linux).
* Optional Encryption - No encryption for maximum performance, or AES-256 encryption for security on shared WiFi network.
* Accept/Reject file request mechanism - No one can send you files without your permission.
* Bidirectional cancellation - Sender or receiver can cancel the transfer at any time.
* Discovery - Automatically discover other devices on the same network.
* Visibility options - Hide your device from others on the network while still being able to send files.
* Drag and Drop (desktop only) - Drag and drop files to send them.
* Concurrent transfers - Send and receive multiple files at the same time.
* Transfer continuation - Resume transfers if connection is lost or transfer is cancelled.

## Why
I created LANd because existing file transfer apps often have one or more of the following limitations:
1. Web-based:
   * Slower transfers due to uploading data via WAN.
   * Concerns about data privacy, as data is sent to a third-party server.
2. Not fully cross-platform:
   * Many file transfer applications lack support for Linux or iOS.
3. Not free:
   * Most of these applications require a purchase or subscription.

## How (Technical Details)
### Kotlin Multiplatform
LANd is built with Kotlin Multiplatform and JetBrains Compose.

### DNS-SD
LANd uses DNS-SD to discover other devices on the same network.

### TCP
LANd uses a custom TCP socket protocol to transfer files.  

## Download/Build
### Android
I don't have the app on the Play Store at the moment, so you will have to build it yourself via Android Studio or Intellij IDEA.

### iOS
I don't want to pay $100/year to publish this app on the App Store at the moment, so you will have to build it yourself.

### Desktop
* MacOS
* Linux
* Windows

## Using LANd
### Select Download Folder
Before you can send or receive files, you must select a download folder. This is where all files you receive will be saved.

### Encryption
You can choose to enable encryption for file transfers. This will encrypt the file before sending it and decrypt it on the receiving end. This is useful if you are on a shared WiFi network and want to ensure that no one can intercept your files.

To toggle encryption click the lock icon in the top right corner of the app. 

### Visibility
You can specify your visibility level to other devices on the network. All devices must be on the same network for discovery
and transferring of files to work.
* Visible (default) - Other devices can see your device and send you files.
* Hidden - Other devices cannot see your device, but you can still send files to them or receive files if your IP address is known.
* Send Only - Other devices cannot see your device or send files to you. You are able to send files.

### Manual Connections
If discovery does not work for some reason, you can manually connect to a device by entering their IP address. Press
the `+` button in the top right corner of the app to manually connect to a device.

### Transferring Files
To transfer a file, click on a discovered device and select one or more files to send. On desktop, you can drag and drop files onto the device to send them.

### Cancelling Transfers
Transfers may either be stopped or deleted. Pressing the stop button will simply stop the transfer where it is. Pressing the
trash button will stop the transfer and delete the partial file on the receiving end.
### Mobile
Long press on a discovered device with an active transfer and press either the stop or trash button.
### Desktop
Hover over a discovered device with an active transfer and press the stop or trash button


## Changelog
[Changelog](CHANGELOG.md)