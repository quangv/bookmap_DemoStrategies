# Moving Average Indicator for Bookmap

A customizable Moving Average indicator that supports multiple calculation methods.

## Features

- **Multiple MA Types**:

  - Simple Moving Average (SMA)
  - Exponential Moving Average (EMA)
  - Weighted Moving Average (WMA)

- **Customizable Settings**:
  - Period (default: 20)
  - Line color (default: Blue #2196F3)
  - Line width (default: 2)

## Installation

1. Build the project using Gradle:

   ```bash
   cd bookmap/Strategies
   ./gradlew build
   ```

2. The compiled JAR will be in `build/libs/`

3. Load the indicator in Bookmap:
   - Go to Settings → Add-ons
   - Add the generated JAR file
   - Enable "Moving Average" indicator

## Usage

### Basic Setup

1. Add the "Moving Average" indicator to your chart
2. Configure settings via the indicator settings panel:
   - **Period**: Number of periods for MA calculation
   - **MA Type**: Choose between SMA, EMA, or WMA
   - **Color**: Line color
   - **Line Width**: Thickness of the MA line

### Calculation Methods

#### Simple Moving Average (SMA)

```
SMA = (P1 + P2 + ... + Pn) / n
```

Average of the last n prices.

#### Exponential Moving Average (EMA)

```
EMA = (Price - PreviousEMA) × Multiplier + PreviousEMA
Multiplier = 2 / (Period + 1)
```

Gives more weight to recent prices.

#### Weighted Moving Average (WMA)

```
WMA = (P1×1 + P2×2 + ... + Pn×n) / (1 + 2 + ... + n)
```

Applies linearly increasing weights to prices.

## Configuration

The indicator uses `MovingAverageSettings.java` for configuration:

```java
public int period = 20;              // MA period
public MAType maType = MAType.EMA;   // MA calculation type
public Color color = Color.BLUE;     // Line color
public int lineWidth = 2;            // Line width
```

## API Integration

The indicator implements:

- `Layer1ApiFinishable` - Cleanup on removal
- `Layer1ApiAdminAdapter` - Admin message handling
- `Layer1ApiInstrumentListener` - Instrument lifecycle events
- `OnlineCalculatable` - Real-time and historical calculations
- `Layer1ConfigSettingsInterface` - Settings persistence
- `Layer1IndicatorColorInterface` - Color scheme management

## File Structure

```
bookmap/Strategies/src/main/java/velox/api/layer1/simpledemo/movingaverage/
├── Layer1ApiMovingAverage.java        # Main indicator implementation
├── MovingAverageSettings.java         # Settings configuration
└── README.md                          # This file
```

## Technical Details

### Real-time Calculation

- Uses `OnlineValueCalculatorAdapter` for live price updates
- Processes each trade event to update MA value
- Efficiently maintains price history with Queue data structure

### Historical Calculation

- Retrieves trade data via `DataStructureInterface`
- Calculates MA values for specified time range
- Handles missing data gracefully

### Performance

- Efficient O(1) calculation for SMA using running sum
- EMA uses recursive formula for optimal performance
- WMA recalculates weights only when needed

## Example Usage

1. **Day Trading**: Use EMA(9) or EMA(21) for quick trend identification
2. **Swing Trading**: Combine SMA(50) and SMA(200) for trend confirmation
3. **Support/Resistance**: WMA can act as dynamic support/resistance levels

## Dependencies

Requires Bookmap API (Layer 1 API):

- `velox.api.layer1.*` packages
- Version: Layer1ApiVersionValue.VERSION2

## License

This indicator follows the Bookmap API licensing terms.

## Author

Created for the QI Trading Platform
