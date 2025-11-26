package velox.api.layer1.aaa.barscount;

import java.awt.Color;

import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1SimpleAttachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.layers.utils.OrderBook;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import velox.api.layer1.simplified.Api;
import velox.api.layer1.simplified.AxisGroup;
import velox.api.layer1.simplified.Bar;
import velox.api.layer1.simplified.BarDataListener;
import velox.api.layer1.simplified.CustomModule;
import velox.api.layer1.simplified.HistoricalDataListener;
import velox.api.layer1.simplified.Indicator;
import velox.api.layer1.simplified.InitialState;
import velox.api.layer1.simplified.IntervalListener;
import velox.api.layer1.simplified.Intervals;
import velox.api.layer1.simplified.Parameter;

/**
 * Bars Count Indicator for Bookmap using Simplified API
 * Inspired by Al Brooks' "Bar Count Number" concept
 * Counts bars since direction change and highlights new highs/lows
 */
@Layer1SimpleAttachable
@Layer1StrategyName("QI Bars Count")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class Layer1ApiBarsCount implements CustomModule, BarDataListener, HistoricalDataListener, IntervalListener {
    
    @Parameter(name = "Lookback Period", step = 1.0)
    public Double lookbackPeriod = 3.0;
    
    @Parameter(name = "Up Color")
    public Color upColor = new Color(56, 142, 60); // Green2
    
    @Parameter(name = "Down Color")
    public Color downColor = new Color(245, 161, 164); // Red5
    
    @Parameter(name = "New High Color")
    public Color newHighColor = new Color(0, 255, 187); // Color5
    
    @Parameter(name = "New Low Color")
    public Color newLowColor = new Color(255, 17, 0); // Color6
    
    @Parameter(name = "Max Count (0=unlimited)", step = 1.0)
    public Double maxCount = 0.0;
    
    @Parameter(name = "Compare With (self/other/both/either)")
    public String compareWith = "self";
    
    @Parameter(name = "Zero Line Color")
    public Color zeroLineColor = new Color(200, 200, 200); // Light gray
    
    @Parameter(name = "Zero Line Width", step = 1.0)
    public Double zeroLineWidth = 2.0;
    
    @Parameter(name = "Interval (seconds)", step = 1.0)
    public Double intervalSeconds = 5.0;
    
    @Parameter(name = "Use 4 Lines (separate new high/low)")
    public Boolean use4Lines = false;
    
    private Indicator upIndicator;
    private Indicator downIndicator;
    private Indicator newHighIndicator;
    private Indicator newLowIndicator;
    private Indicator zeroLineIndicator;
    private BarsCountCalculator calculator;
    private InstrumentInfo info;
    
    @Override
    public void initialize(String alias, InstrumentInfo info, Api api, InitialState initialState) {
        this.info = info;
        Log.info("QI Bars Count: Initializing for " + alias + ", lookback=" + lookbackPeriod.intValue() + ", use4Lines=" + use4Lines);
        
        // Register separate indicators for each state
        upIndicator = api.registerIndicator("Up Count", GraphType.BOTTOM);
        upIndicator.setColor(upColor);
        
        downIndicator = api.registerIndicator("Down Count", GraphType.BOTTOM);
        downIndicator.setColor(downColor);
        
        if (use4Lines) {
            newHighIndicator = api.registerIndicator("New High", GraphType.BOTTOM);
            newHighIndicator.setColor(newHighColor);
            
            newLowIndicator = api.registerIndicator("New Low", GraphType.BOTTOM);
            newLowIndicator.setColor(newLowColor);
        }
        
        // Add a zero line as a reference
        zeroLineIndicator = api.registerIndicator("Zero Line", GraphType.BOTTOM);
        zeroLineIndicator.setColor(zeroLineColor);
        zeroLineIndicator.setWidth(zeroLineWidth.intValue());

        AxisGroup axisGroup = new AxisGroup();
        axisGroup.add(upIndicator);
        axisGroup.add(downIndicator);
        if (use4Lines) {
            axisGroup.add(newHighIndicator);
            axisGroup.add(newLowIndicator);
        }
        axisGroup.add(zeroLineIndicator);
        
        Log.info("QI Bars Count: Indicators registered as bottom panel");
        
        calculator = new BarsCountCalculator(
            lookbackPeriod.intValue(), 
            maxCount.intValue(),
            compareWith,
            upColor,
            downColor,
            newHighColor,
            newLowColor
        );
    }
    
    @Override
    public void stop() {
        Log.info("QI Bars Count: Stopping");
    }
    
    @Override
    public void onBar(OrderBook orderBook, Bar bar) {
        if (calculator == null) {
            Log.warn("QI Bars Count: Calculator is null!");
            return;
        }
        
        double close = bar.getClose();
        
        CountResult result = calculator.addBar(close);
        
        // Display the count as a positive value in the bottom panel
        // Both up and down counts are positive, just different colors
        double countValue = result.count;
        
        // Always add 0 to all indicators to keep them on the same scale
        // Use NaN to make lines invisible when not active, but the scale remains consistent
        if (use4Lines) {
            // 4-line mode: separate indicators for each state
            if (result.isDown) {
                if (result.isNewLow) {
                    newLowIndicator.addPoint(countValue);
                    downIndicator.addPoint(0.0);
                } else {
                    newLowIndicator.addPoint(0.0);
                    downIndicator.addPoint(countValue);
                }
                upIndicator.addPoint(0.0);
                newHighIndicator.addPoint(0.0);
            } else {
                if (result.isNewHigh) {
                    newHighIndicator.addPoint(countValue);
                    upIndicator.addPoint(0.0);
                } else {
                    newHighIndicator.addPoint(0.0);
                    upIndicator.addPoint(countValue);
                }
                downIndicator.addPoint(0.0);
                newLowIndicator.addPoint(0.0);
            }
        } else {
            // 2-line mode: just up and down
            if (result.isDown) {
                downIndicator.addPoint(countValue);
                upIndicator.addPoint(0.0);
            } else {
                upIndicator.addPoint(countValue);
                downIndicator.addPoint(0.0);
            }
        }
        
        // Add zero line reference point
        zeroLineIndicator.addPoint(0.0);
        
        // Log periodically
        if (calculator.getTotalBars() <= 5 || calculator.getTotalBars() % 20 == 0) {
            Log.info("QI Bars Count: bar#" + calculator.getTotalBars() + 
                    ", close=" + String.format("%.2f", close) +
                    ", count=" + result.count +
                    ", isDown=" + result.isDown +
                    ", isNewHigh=" + result.isNewHigh +
                    ", isNewLow=" + result.isNewLow);
        }
    }
    
    @Override
    public void onInterval() {
        // Required by IntervalListener interface
    }
    
    @Override
    public long getInterval() {
        // Convert seconds to nanoseconds
        return (long)(intervalSeconds * 1_000_000_000L);
    }
    
    /**
     * Result of bar count calculation
     */
    private static class CountResult {
        final int count;
        final boolean isDown;
        final boolean isNewHigh;
        final boolean isNewLow;
        final Color color;
        
        CountResult(int count, boolean isDown, boolean isNewHigh, boolean isNewLow, Color color) {
            this.count = count;
            this.isDown = isDown;
            this.isNewHigh = isNewHigh;
            this.isNewLow = isNewLow;
            this.color = color;
        }
        
        public Color getColor() {
            return color;
        }
    }
    
    /**
     * Helper class to calculate bar counts
     */
    private static class BarsCountCalculator {
        private final int lookbackPeriod;
        private final int maxCount;
        private final String compareWith;
        private final Color upColor;
        private final Color downColor;
        private final Color newHighColor;
        private final Color newLowColor;
        
        private int count = 0;
        private int lastHighCount = 0;
        private int lastLowCount = 0;
        private int totalBars = 0;
        private boolean isDown = false;
        private double sumClose = 0.0;
        private final java.util.LinkedList<Double> priceQueue = new java.util.LinkedList<>();
        private double lastSMA = Double.NaN;
        
        public BarsCountCalculator(int lookbackPeriod, int maxCount, String compareWith,
                                 Color upColor, Color downColor, Color newHighColor, Color newLowColor) {
            this.lookbackPeriod = lookbackPeriod;
            this.maxCount = maxCount;
            this.compareWith = compareWith;
            this.upColor = upColor;
            this.downColor = downColor;
            this.newHighColor = newHighColor;
            this.newLowColor = newLowColor;
        }
        
        public int getTotalBars() {
            return totalBars;
        }
        
        public CountResult addBar(double close) {
            totalBars++;
            
            // Calculate simple moving average for direction using a proper queue
            priceQueue.add(close);
            sumClose += close;
            
            // Remove oldest price if we exceed lookback period
            if (priceQueue.size() > lookbackPeriod) {
                double oldestPrice = priceQueue.removeFirst();
                sumClose -= oldestPrice;
            }
            
            double currentSMA = sumClose / priceQueue.size();
            
            // Determine if we're in a down trend (compare close to previous SMA)
            boolean newIsDown = !Double.isNaN(lastSMA) && close < lastSMA;
            lastSMA = currentSMA;
            
            // Check if direction changed
            boolean resetCond = (totalBars > 1) && (newIsDown != isDown);
            
            if (resetCond) {
                // Update last high/low count when direction changes
                if (newIsDown && count > 0) {
                    lastHighCount = count;
                }
                if (!newIsDown && count > 0) {
                    lastLowCount = count;
                }
                count = 0;
            } else if (totalBars > 1) {
                count++;
            }
            
            isDown = newIsDown;
            
            // Check if current count is a new high or new low
            boolean isNewHigh = false;
            boolean isNewLow = false;
            
            if (!isDown) {
                // Up trend - check for new high
                switch (compareWith) {
                    case "self":
                        isNewHigh = count > lastHighCount;
                        break;
                    case "other":
                        isNewHigh = count > lastLowCount;
                        break;
                    case "both":
                        isNewHigh = count > Math.max(lastHighCount, lastLowCount);
                        break;
                    case "either":
                        isNewHigh = count > Math.min(lastHighCount, lastLowCount);
                        break;
                    default:
                        isNewHigh = count > lastHighCount;
                }
            } else {
                // Down trend - check for new low
                switch (compareWith) {
                    case "self":
                        isNewLow = count > lastLowCount;
                        break;
                    case "other":
                        isNewLow = count > lastHighCount;
                        break;
                    case "both":
                        isNewLow = count > Math.max(lastHighCount, lastLowCount);
                        break;
                    case "either":
                        isNewLow = count > Math.min(lastHighCount, lastLowCount);
                        break;
                    default:
                        isNewLow = count > lastLowCount;
                }
            }
            
            // Reset count if it exceeds maxCount
            if (maxCount > 0 && count > maxCount) {
                count = 1;
                isNewHigh = false;
                isNewLow = false;
            }
            
            // Determine color
            Color color;
            if (isDown) {
                color = isNewLow ? newLowColor : downColor;
            } else {
                color = isNewHigh ? newHighColor : upColor;
            }
            
            return new CountResult(count, isDown, isNewHigh, isNewLow, color);
        }
    }
}
