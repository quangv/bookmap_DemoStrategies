package velox.api.layer1.aaa.barscount;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import velox.api.layer1.Layer1ApiAdminAdapter;
import velox.api.layer1.Layer1ApiFinishable;
import velox.api.layer1.Layer1ApiInstrumentListener;
import velox.api.layer1.Layer1ApiProvider;
import velox.api.layer1.Layer1CustomPanelsGetter;
import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1Attachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.common.ListenableHelper;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.data.MarketMode;
import velox.api.layer1.data.StatusInfo;
import velox.api.layer1.data.OrderInfoUpdate;
import velox.api.layer1.data.ExecutionInfo;
import velox.api.layer1.data.BalanceInfo;
import velox.api.layer1.layers.strategies.interfaces.CalculatedResultListener;
import velox.api.layer1.layers.strategies.interfaces.CustomEventAggregatble;
import velox.api.layer1.layers.strategies.interfaces.CustomGeneratedEvent;
import velox.api.layer1.layers.strategies.interfaces.CustomGeneratedEventAliased;
import velox.api.layer1.layers.strategies.interfaces.InvalidateInterface;
import velox.api.layer1.layers.strategies.interfaces.OnlineCalculatable;
import velox.api.layer1.layers.strategies.interfaces.OnlineValueCalculatorAdapter;
import velox.api.layer1.messages.GeneratedEventInfo;
import velox.api.layer1.messages.Layer1ApiUserMessageAddStrategyUpdateGenerator;
import velox.api.layer1.messages.UserMessageLayersChainCreatedTargeted;
import velox.api.layer1.messages.indicators.DataStructureInterface;
import velox.api.layer1.messages.indicators.DataStructureInterface.TreeResponseInterval;
import velox.api.layer1.messages.indicators.IndicatorColorScheme;
import velox.api.layer1.messages.indicators.IndicatorColorScheme.ColorDescription;
import velox.api.layer1.messages.indicators.IndicatorColorScheme.ColorIntervalResponse;
import velox.api.layer1.messages.indicators.IndicatorLineStyle;
import velox.api.layer1.messages.indicators.Layer1ApiDataInterfaceRequestMessage;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import velox.api.layer1.messages.indicators.StrategyUpdateGenerator;
import velox.api.layer1.messages.indicators.SettingsAccess;
import velox.api.layer1.settings.Layer1ConfigSettingsInterface;
import velox.api.layer1.settings.StrategySettingsVersion;
import velox.gui.StrategyPanel;

import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Bars Count Indicator V2 for Bookmap using Advanced API
 * Inspired by Al Brooks' "Bar Count Number" concept
 * This version draws the count numbers directly on the chart using markers
 */
@Layer1Attachable
@Layer1StrategyName("QI Bars Count V2")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class Layer1ApiBarsCountV2 implements
    Layer1ApiFinishable,
    Layer1ApiAdminAdapter,
    Layer1ApiInstrumentListener,
    OnlineCalculatable,
    Layer1CustomPanelsGetter,
    Layer1ConfigSettingsInterface {

    /**
     * Custom event that holds bar count information and can draw itself as a marker
     */
    private static class BarsCountEvent implements CustomGeneratedEvent, DataCoordinateMarker {
        private static final long serialVersionUID = 1L;
        private long time;
        private double price;
        private int count;
        private boolean isDown;
        private boolean isNewHigh;
        private boolean isNewLow;
        private Color upColor;
        private Color downColor;
        private Color newHighColor;
        private Color newLowColor;
        private int fontSize;
        
        public BarsCountEvent(long time) {
            this(time, Double.NaN, 0, false, false, false, 
                 new Color(56, 142, 60), 
                 new Color(245, 161, 164),
                 new Color(0, 255, 187),
                 new Color(255, 17, 0),
                 12);
        }
        
        public BarsCountEvent(long time, double price, int count, boolean isDown, 
                            boolean isNewHigh, boolean isNewLow,
                            Color upColor, Color downColor, 
                            Color newHighColor, Color newLowColor,
                            int fontSize) {
            this.time = time;
            this.price = price;
            this.count = count;
            this.isDown = isDown;
            this.isNewHigh = isNewHigh;
            this.isNewLow = isNewLow;
            this.upColor = upColor;
            this.downColor = downColor;
            this.newHighColor = newHighColor;
            this.newLowColor = newLowColor;
            this.fontSize = fontSize;
        }
        
        public BarsCountEvent(BarsCountEvent other) {
            this(other.time, other.price, other.count, other.isDown, 
                 other.isNewHigh, other.isNewLow,
                 other.upColor, other.downColor, 
                 other.newHighColor, other.newLowColor,
                 other.fontSize);
        }

        public void setTime(long time) {
            this.time = time;
        }
        
        public void update(double price, int count, boolean isDown, 
                          boolean isNewHigh, boolean isNewLow) {
            this.price = price;
            this.count = count;
            this.isDown = isDown;
            this.isNewHigh = isNewHigh;
            this.isNewLow = isNewLow;
        }
        
        @Override
        public long getTime() {
            return time;
        }
        
        @Override
        public Object clone() {
            return new BarsCountEvent(this);
        }

        @Override
        public String toString() {
            return "[" + time + ": count=" + count + ", isDown=" + isDown + "]";
        }

        @Override
        public double getMinY() {
            return price;
        }

        @Override
        public double getMaxY() {
            return price;
        }

        @Override
        public double getValueY() {
            return price;
        }

        @Override
        public Marker makeMarker(Function<Double, Integer> yDataCoordinateToPixelFunction) {
            if (count == 0 || Double.isNaN(price)) {
                Log.info("QI Bars Count V2: makeMarker returning null (count=" + count + ", price=" + price + ")");
                return null;
            }
            
            Log.info("QI Bars Count V2: makeMarker called - count=" + count + ", price=" + price + ", isDown=" + isDown);
            
            // Determine color based on state
            Color textColor;
            if (isDown) {
                textColor = isNewLow ? newLowColor : downColor;
            } else {
                textColor = isNewHigh ? newHighColor : upColor;
            }
            
            String text = String.valueOf(count);
            
            // Create a temporary graphics to measure text size
            BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tempGraphics = tempImage.createGraphics();
            Font font = new Font("SansSerif", Font.BOLD, fontSize);
            tempGraphics.setFont(font);
            FontMetrics fm = tempGraphics.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            int textAscent = fm.getAscent();
            tempGraphics.dispose();
            
            // Create image with padding
            int padding = 4;
            int imageWidth = textWidth + padding * 2;
            int imageHeight = textHeight + padding * 2;
            
            BufferedImage bufferedImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = bufferedImage.createGraphics();
            
            // Clear background (transparent)
            graphics.setBackground(new Color(0, 0, 0, 0));
            graphics.clearRect(0, 0, imageWidth, imageHeight);
            
            // Draw semi-transparent background box
            graphics.setColor(new Color(0, 0, 0, 150));
            graphics.fillRoundRect(0, 0, imageWidth, imageHeight, 6, 6);
            
            // Draw text
            graphics.setFont(font);
            graphics.setColor(textColor);
            graphics.drawString(text, padding, padding + textAscent);
            
            graphics.dispose();
            
            // Center the marker horizontally and position it near the price
            int iconOffsetX = -imageWidth / 2;
            int iconOffsetY = -imageHeight / 2;
            
            return new Marker(price, iconOffsetX, iconOffsetY, bufferedImage);
        }
    }

    public static final CustomEventAggregatble BARS_COUNT_AGGREGATOR = new CustomEventAggregatble() {
        @Override
        public CustomGeneratedEvent getInitialValue(long t) {
            return new BarsCountEvent(t);
        }

        @Override
        public void aggregateAggregationWithValue(CustomGeneratedEvent aggregation, CustomGeneratedEvent value) {
            BarsCountEvent aggregationEvent = (BarsCountEvent) aggregation;
            BarsCountEvent valueEvent = (BarsCountEvent) value;
            
            if (!Double.isNaN(valueEvent.price)) {
                aggregationEvent.update(valueEvent.price, valueEvent.count, 
                                      valueEvent.isDown, valueEvent.isNewHigh, 
                                      valueEvent.isNewLow);
            }
        }

        @Override
        public void aggregateAggregationWithAggregation(CustomGeneratedEvent aggregation1,
                CustomGeneratedEvent aggregation2) {
            BarsCountEvent event1 = (BarsCountEvent) aggregation1;
            BarsCountEvent event2 = (BarsCountEvent) aggregation2;
            
            if (!Double.isNaN(event2.price)) {
                event1.update(event2.price, event2.count, event2.isDown, 
                            event2.isNewHigh, event2.isNewLow);
            }
        }
    };

    private static final String INDICATOR_NAME = "Bars Count V2";
    private static final String TREE_NAME = "Bars Count Tree";
    private static final double MIN_INTERVAL_SECONDS = 0.25;
    private static final double MAX_INTERVAL_SECONDS = 600.0;
    private static final double INTERVAL_STEP_SECONDS = 0.25;
    private static final long NANOS_IN_SECOND = 1_000_000_000L;
    private static final long MIN_INTERVAL_NANOS = 1_000_000L;
    private static final String SETTINGS_PANEL_TITLE = "Bars Count Settings";
    
    // Parameters - would be configurable in a real implementation
    private int lookbackPeriod = 3;
    private int maxCount = 0;
    private String compareWith = "self";
    private Color upColor = new Color(56, 142, 60);
    private Color downColor = new Color(245, 161, 164);
    private Color newHighColor = new Color(0, 255, 187);
    private Color newLowColor = new Color(255, 17, 0);
    private int fontSize = 12;
    
    private Layer1ApiProvider provider;
    private DataStructureInterface dataStructureInterface;
    private Map<String, BarsCountCalculator> calculators = new HashMap<>();
    private Map<String, BarAccumulator> barAccumulators = new HashMap<>();
    private Map<String, Long> lastEventTimeByAlias = new HashMap<>();
    private Map<String, InvalidateInterface> invalidateInterfaceMap = new HashMap<>();
    private Map<String, BarsCountSettings> settingsMap = new ConcurrentHashMap<>();
    private SettingsAccess settingsAccess;
    private String indicatorUserName;
    private double defaultIntervalSeconds = 5.0;
    
    public Layer1ApiBarsCountV2(Layer1ApiProvider provider) {
        this.provider = provider;
        ListenableHelper.addListeners(provider, this);
        Log.info("QI Bars Count V2: Constructor called");
    }

    @Override
    public void finish() {
        Log.info("QI Bars Count V2: Finishing");
        provider.sendUserMessage(getGeneratorMessage(false));
        provider.sendUserMessage(buildIndicatorRemoveMessage());
    }
    
    @Override
    public void onInstrumentAdded(String alias, InstrumentInfo instrumentInfo) {}
    
    @Override
    public void onInstrumentRemoved(String alias) {
        clearInstrumentState(alias);
    }
    
    @Override
    public void onInstrumentNotFound(String symbol, String exchange, String type) {}
    
    @Override
    public void onInstrumentAlreadySubscribed(String symbol, String exchange, String type) {}

    private void resetInstrumentState(String alias) {
        if (alias == null) {
            return;
        }
        calculators.remove(alias);
        barAccumulators.remove(alias);
        lastEventTimeByAlias.remove(alias);
    }

    private void clearInstrumentState(String alias) {
        resetInstrumentState(alias);
        if (alias != null) {
            settingsMap.remove(alias);
        }
    }

    private void requestInvalidate(String alias) {
        if (alias == null) {
            return;
        }
        InvalidateInterface invalidateInterface = invalidateInterfaceMap.get(alias);
        if (invalidateInterface != null) {
            invalidateInterface.invalidate();
        }
    }

    private long getIntervalNanos(String alias) {
        double intervalSeconds = getSettingsFor(alias).getIntervalSecondsOrDefault(defaultIntervalSeconds);
        long interval = (long) (intervalSeconds * NANOS_IN_SECOND);
        return Math.max(interval, MIN_INTERVAL_NANOS);
    }

    private BarsCountSettings getSettingsFor(String alias) {
        if (alias == null) {
            return createDefaultSettings();
        }
        return settingsMap.compute(alias, (key, existing) -> {
            BarsCountSettings settings = existing;
            if (settings == null) {
                settings = loadSettings(key);
            }
            if (settings.getIntervalSeconds() <= 0) {
                settings.setIntervalSeconds(defaultIntervalSeconds);
            }
            return settings;
        });
    }

    private BarsCountSettings loadSettings(String alias) {
        BarsCountSettings settings = null;
        if (settingsAccess != null) {
            settings = (BarsCountSettings) settingsAccess.getSettings(alias, INDICATOR_NAME, BarsCountSettings.class);
        }
        if (settings == null) {
            settings = createDefaultSettings();
        }
        return settings;
    }

    private BarsCountSettings createDefaultSettings() {
        BarsCountSettings settings = new BarsCountSettings();
        settings.setIntervalSeconds(defaultIntervalSeconds);
        return settings;
    }

    private void settingsChanged(String alias, BarsCountSettings settings) {
        if (alias == null || settings == null) {
            return;
        }
        settingsMap.put(alias, settings);
        if (settingsAccess != null) {
            settingsAccess.setSettings(alias, INDICATOR_NAME, settings, BarsCountSettings.class);
        }
    }

    @Override
    public void onUserMessage(Object data) {
        if (data.getClass() == UserMessageLayersChainCreatedTargeted.class) {
            UserMessageLayersChainCreatedTargeted message = (UserMessageLayersChainCreatedTargeted) data;
            if (message.targetClass == getClass()) {
                Log.info("QI Bars Count V2: Layers chain created, initializing...");
                provider.sendUserMessage(new Layer1ApiDataInterfaceRequestMessage(dataStructureInterface -> {
                    this.dataStructureInterface = dataStructureInterface;
                    Log.info("QI Bars Count V2: Data structure interface received");
                }));
                provider.sendUserMessage(getGeneratorMessage(true));
                Log.info("QI Bars Count V2: Generator message sent");
                provider.sendUserMessage(buildIndicatorAddMessage());
                Log.info("QI Bars Count V2: Indicator message sent");
            }
        }
    }

    private Layer1ApiUserMessageAddStrategyUpdateGenerator getGeneratorMessage(boolean isAdd) {
        return new Layer1ApiUserMessageAddStrategyUpdateGenerator(
            Layer1ApiBarsCountV2.class, 
            TREE_NAME, 
            isAdd, 
            true, 
            new StrategyUpdateGenerator() {
                private Consumer<CustomGeneratedEventAliased> consumer;
                private long time = 0;
                
                @Override
                public void setGeneratedEventsConsumer(Consumer<CustomGeneratedEventAliased> consumer) {
                    this.consumer = consumer;
                }
                
                @Override
                public Consumer<CustomGeneratedEventAliased> getGeneratedEventsConsumer() {
                    return consumer;
                }
                
                @Override
                public void onStatus(StatusInfo statusInfo) {}
                
                @Override
                public void onOrderUpdated(OrderInfoUpdate orderInfoUpdate) {}
                
                @Override
                public void onOrderExecuted(ExecutionInfo executionInfo) {}
                
                @Override
                public void onBalance(BalanceInfo balanceInfo) {}
                
                @Override
                public void onTrade(String alias, double price, int size, TradeInfo tradeInfo) {
                    BarAccumulator accumulator = barAccumulators.computeIfAbsent(alias, key -> {
                        long intervalNs = getIntervalNanos(key);
                        double intervalSec = intervalNs / (double) NANOS_IN_SECOND;
                        Log.info("QI Bars Count V2: Creating accumulator for " + alias + 
                                 " with interval=" + String.format("%.2f", intervalSec) + "s (" + intervalNs + "ns)");
                        return new BarAccumulator(intervalNs);
                    });
                    CompletedBar completedBar = accumulator.onTrade(time, price);
                    if (completedBar == null) {
                        return;
                    }

                    BarsCountCalculator calculator = calculators.computeIfAbsent(alias, key -> {
                        Log.info("QI Bars Count V2: Created new calculator for " + alias);
                        return new BarsCountCalculator(
                                lookbackPeriod, maxCount, compareWith,
                                upColor, downColor, newHighColor, newLowColor, fontSize);
                    });

                    CountResult result = calculator.addBar(completedBar.closePrice);

                    long eventTime = Math.max(completedBar.closeTime, time);
                    Long lastEventTime = lastEventTimeByAlias.get(alias);
                    if (lastEventTime != null && eventTime <= lastEventTime) {
                        eventTime = lastEventTime + 1;
                    }
                    lastEventTimeByAlias.put(alias, eventTime);

                    BarsCountEvent event = new BarsCountEvent(
                        eventTime,
                        completedBar.closePrice,
                        result.count, result.isDown,
                        result.isNewHigh, result.isNewLow,
                        upColor, downColor, newHighColor, newLowColor, fontSize
                    );

                    consumer.accept(new CustomGeneratedEventAliased(event, alias));

                    if (calculator.getTotalBars() <= 5 || calculator.getTotalBars() % 20 == 0) {
                        Log.info("QI Bars Count V2: " + alias + " bar#" + calculator.getTotalBars() +
                                ", close=" + String.format("%.2f", completedBar.closePrice) +
                                ", count=" + result.count +
                                ", isDown=" + result.isDown +
                                ", isNewHigh=" + result.isNewHigh +
                                ", isNewLow=" + result.isNewLow);
                    }
                }
                
                @Override
                public void onMarketMode(String alias, MarketMode marketMode) {}
                
                @Override
                public void onDepth(String alias, boolean isBid, int price, int size) {}
                
                @Override
                public void onInstrumentAdded(String alias, InstrumentInfo instrumentInfo) {}
                
                @Override
                public void onInstrumentRemoved(String alias) {
                    clearInstrumentState(alias);
                }
                
                @Override
                public void onInstrumentNotFound(String symbol, String exchange, String type) {}
                
                @Override
                public void onInstrumentAlreadySubscribed(String symbol, String exchange, String type) {}
                
                @Override
                public void onUserMessage(Object data) {}
                
                @Override
                public void setTime(long time) {
                    this.time = time;
                }
            },
            new GeneratedEventInfo[] {
                new GeneratedEventInfo(
                    BarsCountEvent.class, 
                    BarsCountEvent.class, 
                    BARS_COUNT_AGGREGATOR
                )
            }
        );
    }

    private Layer1ApiUserMessageModifyIndicator buildIndicatorAddMessage() {
        String colorName = "BarsCount";
        Layer1ApiUserMessageModifyIndicator message = Layer1ApiUserMessageModifyIndicator
            .builder(Layer1ApiBarsCountV2.class, INDICATOR_NAME)
            .setIsAdd(true)
            .setGraphType(GraphType.PRIMARY)
            .setOnlineCalculatable(this)
            .setIndicatorLineStyle(IndicatorLineStyle.NONE)
            .setIndicatorColorScheme(new IndicatorColorScheme() {
                @Override
                public ColorDescription[] getColors() {
                    return new ColorDescription[] {
                        new ColorDescription(Layer1ApiBarsCountV2.class, colorName, upColor, false)
                    };
                }

                @Override
                public ColorIntervalResponse getColorIntervalsList(double valueFrom, double valueTo) {
                    return new ColorIntervalResponse(new String[] {colorName}, new double[] {});
                }

                @Override
                public String getColorFor(Double value) {
                    return colorName;
                }
            })
            .build();
        indicatorUserName = message.userName;
        return message;
    }

    private Layer1ApiUserMessageModifyIndicator buildIndicatorRemoveMessage() {
        String userName = indicatorUserName != null ? indicatorUserName : INDICATOR_NAME;
        return new Layer1ApiUserMessageModifyIndicator(Layer1ApiBarsCountV2.class, userName, false);
    }

    @Override
    public void acceptSettingsInterface(SettingsAccess settingsAccess) {
        this.settingsAccess = settingsAccess;
        if (settingsAccess != null) {
            settingsMap.replaceAll((alias, existing) -> loadSettings(alias));
        }
    }

    @Override
    public StrategyPanel[] getCustomGuiFor(String alias, String indicatorName) {
        if (alias == null) {
            return new StrategyPanel[0];
        }

        StrategyPanel panel = new StrategyPanel(SETTINGS_PANEL_TITLE, new GridBagLayout());
        panel.setLayout(new GridBagLayout());

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = 0;
        labelConstraints.insets = new Insets(5, 5, 5, 5);
        labelConstraints.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Interval (sec)"), labelConstraints);

        double currentInterval = getSettingsFor(alias).getIntervalSecondsOrDefault(defaultIntervalSeconds);
        SpinnerNumberModel model = new SpinnerNumberModel(currentInterval, MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS, INTERVAL_STEP_SECONDS);
        JSpinner intervalSpinner = new JSpinner(model);

        GridBagConstraints spinnerConstraints = new GridBagConstraints();
        spinnerConstraints.gridx = 1;
        spinnerConstraints.gridy = 0;
        spinnerConstraints.weightx = 1;
        spinnerConstraints.insets = new Insets(5, 5, 5, 5);
        spinnerConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(intervalSpinner, spinnerConstraints);

        ChangeListener intervalListener = event -> {
            Number value = (Number) intervalSpinner.getValue();
            double newInterval = value.doubleValue();
            BarsCountSettings settings = getSettingsFor(alias);
            double previous = settings.getIntervalSecondsOrDefault(defaultIntervalSeconds);
            if (Double.compare(previous, newInterval) == 0) {
                return;
            }
            Log.info("QI Bars Count V2: Interval changed for " + alias + 
                     " from " + String.format("%.2f", previous) + "s to " + 
                     String.format("%.2f", newInterval) + "s");
            settings.setIntervalSeconds(newInterval);
            settingsChanged(alias, settings);
            resetInstrumentState(alias);
            requestInvalidate(alias);
        };
        intervalSpinner.addChangeListener(intervalListener);

        return new StrategyPanel[] {panel};
    }

    @Override
    public void calculateValuesInRange(String indicatorName, String alias, long t0, long intervalWidth,
            int intervalsNumber, CalculatedResultListener listener) {
        if (dataStructureInterface == null) {
            listener.setCompleted();
            return;
        }
        
        Class<?>[] interestingEvents = new Class<?>[] { BarsCountEvent.class };
        List<TreeResponseInterval> result = dataStructureInterface.get(
            Layer1ApiBarsCountV2.class, 
            TREE_NAME, 
            t0,
            intervalWidth, 
            intervalsNumber, 
            alias, 
            interestingEvents
        );
        
        for (TreeResponseInterval interval : result) {
            Object event = interval.events.get(BarsCountEvent.class.toString());
            if (event != null) {
                BarsCountEvent barsCountEvent = (BarsCountEvent) event;
                // The marker will be drawn automatically by the DataCoordinateMarker interface
                listener.provideResponse(barsCountEvent);
            }
        }
        
        listener.setCompleted();
    }

    @Override
    public OnlineValueCalculatorAdapter createOnlineValueCalculator(String indicatorName, String indicatorAlias, 
            long time, Consumer<Object> listener, InvalidateInterface invalidateInterface) {
        invalidateInterfaceMap.put(indicatorAlias, invalidateInterface);
        
        if (dataStructureInterface == null) {
            return new OnlineValueCalculatorAdapter() {};
        }
        
        Class<?>[] interestingEvents = new Class<?>[] { BarsCountEvent.class };
        TreeResponseInterval startEvents = dataStructureInterface.get(
            Layer1ApiBarsCountV2.class, 
            TREE_NAME, 
            time, 
            indicatorAlias, 
            interestingEvents
        );
        
        return new OnlineValueCalculatorAdapter() {
            @Override
            public void onUserMessage(Object data) {
                if (data instanceof CustomGeneratedEventAliased) {
                    CustomGeneratedEventAliased aliasedEvent = (CustomGeneratedEventAliased) data;
                    if (indicatorAlias.equals(aliasedEvent.alias) && aliasedEvent.event instanceof BarsCountEvent) {
                        BarsCountEvent event = (BarsCountEvent) aliasedEvent.event;
                        listener.accept(event);
                    }
                }
            }
        };
    }

    @StrategySettingsVersion(currentVersion = 1, compatibleVersions = {})
    public static class BarsCountSettings {
        private double intervalSeconds;

        public double getIntervalSeconds() {
            return intervalSeconds;
        }

        public double getIntervalSecondsOrDefault(double defaultValue) {
            return intervalSeconds > 0 ? intervalSeconds : defaultValue;
        }

        public void setIntervalSeconds(double intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }
    }

    /**
     * Result of bar count calculation
     */
    private static class CountResult {
        final int count;
        final boolean isDown;
        final boolean isNewHigh;
        final boolean isNewLow;
        
        CountResult(int count, boolean isDown, boolean isNewHigh, boolean isNewLow) {
            this.count = count;
            this.isDown = isDown;
            this.isNewHigh = isNewHigh;
            this.isNewLow = isNewLow;
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
        private final int fontSize;
        
        private int count = 0;
        private int lastHighCount = 0;
        private int lastLowCount = 0;
        private int totalBars = 0;
        private boolean isDown = false;
        private double sumClose = 0.0;
        private final java.util.LinkedList<Double> priceQueue = new java.util.LinkedList<>();
        private double lastSMA = Double.NaN;
        
        public BarsCountCalculator(int lookbackPeriod, int maxCount, String compareWith,
                                 Color upColor, Color downColor, Color newHighColor, 
                                 Color newLowColor, int fontSize) {
            this.lookbackPeriod = lookbackPeriod;
            this.maxCount = maxCount;
            this.compareWith = compareWith;
            this.upColor = upColor;
            this.downColor = downColor;
            this.newHighColor = newHighColor;
            this.newLowColor = newLowColor;
            this.fontSize = fontSize;
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
            
            return new CountResult(count, isDown, isNewHigh, isNewLow);
        }
    }

    private static class CompletedBar {
        final long closeTime;
        final double closePrice;

        CompletedBar(long closeTime, double closePrice) {
            this.closeTime = closeTime;
            this.closePrice = closePrice;
        }
    }

    private static class BarAccumulator {
        private final long intervalNs;
        private long currentBucketStart = Long.MIN_VALUE;
        private double lastPrice = Double.NaN;

        BarAccumulator(long intervalNs) {
            this.intervalNs = intervalNs;
        }

        CompletedBar onTrade(long time, double price) {
            long bucket = align(time);
            if (currentBucketStart == Long.MIN_VALUE) {
                currentBucketStart = bucket;
            }
            CompletedBar completed = null;
            while (bucket > currentBucketStart && !Double.isNaN(lastPrice)) {
                long barCloseTime = currentBucketStart + intervalNs;
                completed = new CompletedBar(barCloseTime, lastPrice);
                currentBucketStart += intervalNs;
            }
            lastPrice = price;
            return completed;
        }

        private long align(long time) {
            return (time / intervalNs) * intervalNs;
        }
    }
}
