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
   - Enter your SSH server details (host, port, username, password)
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

## Security Note

Passwords are stored in plain text in the local configuration file. For production use, consider implementing more secure credential storage or using SSH key-based authentication.
