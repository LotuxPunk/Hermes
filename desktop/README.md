# Hermes Desktop

A Compose Multiplatform desktop application for managing Hermes mail templates and configurations via SSH.

## Features

- **SSH Connectivity**: Connect to remote servers via SSH to manage files
- **Template Management**: Browse, edit, create, and delete mail templates (.hbs files)
- **Mail Config Management**: Manage mail configuration files (JSON)
- **Contact Form Config Management**: Manage contact form configuration files (JSON)
- **User-Friendly Interface**: Modern Material 3 design with intuitive navigation
- **Configuration Persistence**: Saves SSH connection settings locally

## Requirements

- Java 21 or later
- SSH access to the Hermes server

## Building

To build the desktop application:

```bash
cd desktop
./gradlew desktop:packageDistributionForCurrentOS
```

This will create a native installer for your platform in `desktop/build/compose/binaries/main/`.

## Running

To run the application without building an installer:

```bash
./gradlew desktop:run
```

## Usage

1. **Connect to SSH Server**:
   - Click the "Connect" button in the top right
   - Enter your SSH server details (host, port, username)
   - Choose authentication method:
     - **Password**: Enter your password
     - **Private Key**: Select your private key file (e.g., `~/.ssh/id_rsa`) and optionally enter passphrase
   - Specify the remote paths for templates, mail configs, and contact form configs
   - Click "Connect"

2. **Browse Files**:
   - Use the tabs to switch between Templates, Mail Configs, and Contact Form Configs
   - Click on folders to navigate
   - Click on files to view and edit their content

3. **Edit Files**:
   - Select a file from the file browser
   - Edit the content in the editor on the right
   - Click "Save" to upload changes to the server

4. **Create New Files**:
   - Click the "+" button in the file browser
   - Enter the file name and optional content
   - Click "Create"

5. **Delete Files**:
   - Click the delete icon next to a file in the file browser
   - Confirm the deletion

## Configuration

SSH connection settings and remote paths are saved locally in:
- Linux/macOS: `~/.hermes-desktop/config.json`
- Windows: `%USERPROFILE%\.hermes-desktop\config.json`

Example configuration with password authentication:
```json
{
  "host": "example.com",
  "port": 22,
  "username": "user",
  "password": "password",
  "usePrivateKey": false,
  "privateKeyPath": "",
  "privateKeyPassphrase": "",
  "templatesPath": "/var/hermes/templates",
  "mailConfigsPath": "/var/hermes/mail-configs",
  "contactFormConfigsPath": "/var/hermes/contact-form-configs"
}
```

Example configuration with private key authentication:
```json
{
  "host": "example.com",
  "port": 22,
  "username": "user",
  "password": "",
  "usePrivateKey": true,
  "privateKeyPath": "/home/user/.ssh/id_rsa",
  "privateKeyPassphrase": "",
  "templatesPath": "/var/hermes/templates",
  "mailConfigsPath": "/var/hermes/mail-configs",
  "contactFormConfigsPath": "/var/hermes/contact-form-configs"
}
```

## Architecture

The desktop application follows MVVM architecture:
- **Model**: SSH configuration and file data models
- **ViewModel**: Application state and business logic using Kotlin coroutines
- **View**: Compose UI components with Material 3 design

Key technologies:
- **Compose Multiplatform 1.7.1**: Cross-platform UI framework
- **SSHJ 0.38.0**: SSH and SFTP operations
- **Kotlinx Serialization**: JSON configuration persistence
- **Kotlin Coroutines**: Asynchronous operations

## Security Note

**Password Storage**: When using password authentication, passwords are stored in plain text in the local configuration file.

**Private Key Authentication (Recommended)**: For better security, use SSH private key authentication instead of passwords. This is the recommended approach for production use. The private key itself is not stored in the configuration file - only the path to the key file is saved.
