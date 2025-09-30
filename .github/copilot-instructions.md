# GitHub Copilot Instructions for Freenov Kale Kaj Robot

## Project Overview
This is a Raspberry Pi-based robot control system that manages various hardware components including motors, LEDs, sensors, and cameras. The system supports different hardware versions and Raspberry Pi models.

## Architecture

### Core Components
- **setup.py**: Main installation and configuration script for hardware setup
- **Server/**: Contains the main server application and hardware control modules
- **Libs/**: Third-party libraries, particularly rpi-ws281x-python for LED control

### Key Modules
- `parameter.py`: Hardware version management and configuration
- `car.py`: Main robot movement control
- `motor.py`: Motor control via PCA9685 PWM controller
- `led.py`: LED strip control with version-specific implementations
- `server.py`: Main server application
- `camera.py`: Camera interface management
- `ultrasonic.py`, `infrared.py`: Sensor interfaces

## Hardware Abstraction
The system uses a parameter-based approach to handle different hardware configurations:
- **Connect Version**: 1 or 2 (different PCB versions)
- **PCB Version**: 1 or 2 (different circuit board layouts)
- **Pi Version**: 1, 2, or 3 (Raspberry Pi 3, 4, or 5)

Configuration is stored in `params.json` and managed by `ParameterManager` class.

## Key Design Patterns

### Hardware Version Detection
```python
# Always check hardware compatibility before initializing components
param = ParameterManager()
connect_version = param.get_connect_version()
pi_version = param.get_pi_version()

# Version-specific initialization
if connect_version == 1 and pi_version != 2:  # v1 not supported on Pi 5
    # Handle unsupported combination
```

### I2C Communication
- Motors use PCA9685 at address 0x40
- ADC uses ADS7830 for analog readings
- Always use I2C bus 1 (default on Raspberry Pi)

### GPIO Management
- Ultrasonic sensors use specific GPIO pins
- Infrared sensors use GPIO for digital line detection
- Camera requires specific dtoverlay configuration

## Coding Guidelines

### Error Handling
- Always handle hardware initialization failures gracefully
- Provide fallback behavior for unsupported hardware combinations
- Log configuration errors clearly for debugging

### File Operations
- Use backup_file() function before modifying system files like /boot/firmware/config.txt
- Validate file existence before operations
- Handle permission issues (many operations require sudo)

### Hardware Initialization
- Initialize hardware components in a specific order (I2C first, then GPIO, then specialized interfaces)
- Validate hardware versions before attempting to use version-specific features
- Provide meaningful error messages for hardware compatibility issues

### Python Style
- Use type hints for parameters (e.g., `param_name: str`, `value: any`)
- Follow existing docstring format with clear parameter descriptions
- Use class-based organization for hardware components

## Testing Considerations
- Test on different Raspberry Pi models when possible
- Mock hardware interfaces for unit testing
- Validate parameter file operations
- Test hardware version detection logic

## Security Notes
- Many operations require sudo privileges
- Be careful with file system modifications
- Validate user input for hardware configuration

## Common Issues to Avoid
- Don't assume hardware availability without version checking
- Always backup configuration files before modification
- Handle import errors for hardware-specific libraries gracefully
- Don't hardcode GPIO pins - use the existing pin mapping system

## Development Workflow
1. Check hardware compatibility first
2. Use parameter management system for configuration
3. Test with different hardware versions if possible
4. Validate file operations and permissions
5. Provide clear error messages for hardware issues