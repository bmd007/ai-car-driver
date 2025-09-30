# Robot Platform Hardware Communication Guide

## 1. Motor Control (PWM via PCA9685)

**Hardware:** PCA9685 16-Channel PWM controller connected via I2C  
**I2C Address:** 0x40  
**Bus:** I2C Bus 1 (default on Raspberry Pi)

### Motor Channel Mapping

| Motor Position | PCA9685 Channel | Movement Direction |
|---|---|---|
| Front Left | 0 | Positive: Forward, Negative: Backward |
| Back Left | 1 | Positive: Forward, Negative: Backward |
| Front Right | 2 | Positive: Forward, Negative: Backward |
| Back Right | 3 | Positive: Forward, Negative: Backward |

### Communication Protocol

- Initialize PCA9685: Write 0x00 to register 0x00 (MODE1)
- Set PWM frequency to 50Hz for motors
- PWM values range: 0-4095 (12-bit resolution)
- Motor speed control: 0 = stop, ±2000 = medium speed, ±4095 = max speed

### Movement Examples

- **Forward:** All motors positive values (e.g., 2000, 2000, 2000, 2000)
- **Backward:** All motors negative values (e.g., -2000, -2000, -2000, -2000)
- **Turn Left:** Left motors negative, right motors positive
- **Turn Right:** Left motors positive, right motors negative

## 2. Servo Control (PWM via PCA9685)

**Hardware:** Same PCA9685 controller as motors

| Servo | PCA9685 Channel | Purpose |
|---|---|---|
| Servo 0 | 4 | Ultrasonic sensor rotation |
| Servo 1 | 5 | General purpose |

### Control Method

- PWM frequency: 50Hz
- Pulse width: 500-2500 microseconds
- Angle range: 0-180 degrees
- Calculation: `pulse_value = (pulse_microseconds * 4096) / 20000`

## 3. LED Control

**Hardware:** WS2812B LED strip (8 LEDs)

### Connection Options

| Connect Version | Pi Version | Interface | GPIO Pin |
|---|---|---|---|
| 1 | 4 or earlier | Direct GPIO (PWM) | GPIO 18 |
| 2 | Any | SPI | GPIO 10 (MOSI) |

### Communication Protocol

- **WS2812B Protocol:** Each LED requires 24 bits (8 bits per RGB channel)
- **SPI Method:** Convert each bit to SPI timing pattern
- **Color Format:** Can be RGB, GRB, or other arrangements depending on version
- **Control:** Send data for all 8 LEDs in sequence

### LED Operations

- **Set Individual LED:** Send RGB values (0-255 each) for specific LED index
- **Set All LEDs:** Send same RGB values to all 8 LEDs
- **Brightness Control:** Scale RGB values by brightness factor (0-255)

## 4. Ultrasonic Sensor

**Hardware:** HC-SR04 ultrasonic distance sensor

| Pin Function | GPIO Pin | Purpose |
|---|---|---|
| Trigger | GPIO 27 | Send 10μs pulse to start measurement |
| Echo | GPIO 22 | Receive pulse duration proportional to distance |

### Measurement Process

1. Set trigger pin HIGH for 10 microseconds
2. Set trigger pin LOW
3. Measure duration of HIGH pulse on echo pin
4. Calculate distance: `distance_cm = (pulse_duration * 34300) / 2000000`

## 5. Infrared Line Sensors

**Hardware:** 3 digital infrared sensors for line following

| Sensor Position | GPIO Pin | Reading |
|---|---|---|
| Left (Channel 1) | GPIO 14 | 0 = Line detected, 1 = No line |
| Center (Channel 2) | GPIO 15 | 0 = Line detected, 1 = No line |
| Right (Channel 3) | GPIO 23 | 0 = Line detected, 1 = No line |

### Reading Method

- Read GPIO pins as digital inputs
- Combine readings into single value: `combined = (left << 2) | (center << 1) | right`
- Result range: 0-7, where each bit represents a sensor state

## 6. ADC (Analog-to-Digital Converter)

**Hardware:** ADS7830 8-bit ADC connected via I2C  
**I2C Address:** 0x4B

| Channel | Purpose | Voltage Range |
|---|---|---|
| 0 | Left photoresistor | 0-3.3V (PCB v2) / 0-5V (PCB v1) |
| 1 | Right photoresistor | 0-3.3V (PCB v2) / 0-5V (PCB v1) |
| 2 | Battery voltage divider | 0-3.3V (PCB v2) / 0-5V (PCB v1) |

### Reading Process

1. Send channel selection command to ADS7830
2. Read 8-bit digital value (0-255)
3. Convert to voltage: `voltage = (adc_value / 255) * reference_voltage`
4. Reference voltage depends on PCB version (3.3V or 5V)

## 7. Buzzer Control

**Hardware:** PWM-controlled buzzer  
**Control:** Via PCA9685 PWM controller, channel varies by configuration

### Sound Generation

- Set PWM frequency to desired tone frequency (e.g., 440Hz for A note)
- Set duty cycle to 50% for maximum volume
- Control duration by timing the PWM on/off

## 8. Camera Control

**Hardware:** Raspberry Pi Camera Module  
**Interface:** CSI (Camera Serial Interface)

### Operations

- **Image Capture:** Configure resolution and capture single frame
- **Video Stream:** Continuous JPEG or H.264 encoding
- **Configuration:** Set resolution, flip options, preview settings

## 9. I2C Bus Configuration

**Default I2C Bus:** Bus 1 (/dev/i2c-1)  
**GPIO Pins:** SDA (GPIO 2), SCL (GPIO 3)

| Device | I2C Address | Purpose |
|---|---|---|
| PCA9685 | 0x40 | PWM controller for motors/servos |
| ADS7830 | 0x4B | 8-channel ADC |

## 10. Platform Versions and Compatibility

### Hardware Versions

| Component | Version 1 | Version 2 |
|---|---|---|
| Connect Board | Direct GPIO control | Enhanced I2C/SPI |
| PCB | 5V reference | 3.3V reference |
| Raspberry Pi | Pi 4 and earlier | Pi 5 support |

### Key Differences

- **LED Control:** v1 uses GPIO PWM, v2 uses SPI
- **ADC Reference:** v1 uses 5V, v2 uses 3.3V
- **Pi 5 Compatibility:** Only supported with Connect v2

## 11. Implementation Summary

### Required Interfaces

- **I2C:** Motor/servo control (PCA9685), ADC readings (ADS7830)
- **GPIO:** Ultrasonic sensor, infrared sensors
- **SPI:** LED control (version 2 only)
- **PWM:** LED control (version 1 only)
- **CSI:** Camera interface

### Initialization Sequence

1. Detect platform version from configuration file
2. Initialize I2C bus for PCA9685 and ADS7830
3. Configure GPIO pins for sensors
4. Initialize appropriate LED interface (GPIO/SPI)
5. Set up camera interface
6. Configure PWM frequencies and initial states