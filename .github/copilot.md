# Copilot Instructions

## Project Context
Raspberry Pi robot control system with modular hardware support for different PCB and Pi versions.

## Key Architecture Points
- **Parameter Management**: All hardware initialization depends on `ParameterManager` for version detection
- **Hardware Versioning**: Support for Connect v1/v2, PCB v1/v2, Pi versions 1-3 (Pi 3/4/5)
- **I2C Devices**: PCA9685 (motors) at 0x40, ADS7830 (ADC) for sensors
- **Version Compatibility**: Connect v1 + Pi 5 = unsupported combination

## Code Patterns
```python
# Always start with version detection
param = ParameterManager()
connect_version = param.get_connect_version()
pi_version = param.get_pi_version()

# Check compatibility before hardware init
if connect_version == 1 and pi_version == 3:
    self.is_support_function = False
    return
```

## Important Guidelines
- Use type hints: `param_name: str`, `value: any`
- Handle hardware failures gracefully with fallback behavior
- Backup system files before modification (use `backup_file()`)
- Initialize I2C first, then GPIO, then specialized interfaces
- Always validate hardware compatibility before feature use

## Common Patterns
- Motor control: 4 channels (0-3) via PCA9685, PWM range ±4095
- LED control: Version-specific (GPIO PWM vs SPI)
- Camera: Requires dtoverlay configuration for specific models (ov5647, imx219)
- Sensors: GPIO-based with specific pin mappings