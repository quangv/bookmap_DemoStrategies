package velox.api.layer1.simpledemo.movingaverage;

import java.awt.Color;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

import velox.api.layer1.Layer1ApiAdminAdapter;
import velox.api.layer1.Layer1ApiFinishable;
import velox.api.layer1.Layer1ApiInstrumentListener;
import velox.api.layer1.Layer1ApiProvider;
import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1Attachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.common.ListenableHelper;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.layers.strategies.interfaces.CalculatedResultListener;
import velox.api.layer1.layers.strategies.interfaces.InvalidateInterface;
import velox.api.layer1.layers.strategies.interfaces.Layer1IndicatorColorInterface;
import velox.api.layer1.layers.strategies.interfaces.OnlineCalculatable;
import velox.api.layer1.layers.strategies.interfaces.OnlineValueCalculatorAdapter;
import velox.api.layer1.messages.UserMessageLayersChainCreatedTargeted;
import velox.api.layer1.messages.indicators.DataStructureInterface;
import velox.api.layer1.messages.indicators.IndicatorColorScheme;
import velox.api.layer1.messages.indicators.IndicatorLineStyle;
import velox.api.layer1.messages.indicators.Layer1ApiDataInterfaceRequestMessage;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import velox.api.layer1.simpledemo.movingaverage.MovingAverageSettings.MAType;
import velox.colors.ColorsChangedListener;

/**
 * Moving Average Indicator for Bookmap
 * Supports SMA, EMA, and WMA calculation methods
 */
@Layer1Attachable
@Layer1StrategyName("QI Moving Average")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class Layer1ApiMovingAverage implements
    Layer1ApiFinishable,
    Layer1ApiAdminAdapter,
    Layer1ApiInstrumentListener,
    OnlineCalculatable,
    Layer1IndicatorColorInterface {
    
    private static final String INDICATOR_NAME = "MA";
    private static final String COLOR_NAME = "MA_LINE";
    
    private Layer1ApiProvider provider;
    private DataStructureInterface dataStructureInterface;
    
    private Map<String, MovingAverageSettings> settingsMap = new HashMap<>();
    private Map<String, InvalidateInterface> invalidateInterfaceMap = new HashMap<>();
    private Map<String, String> indicatorsFullNameToUserName = new HashMap<>();
    private Map<String, String> indicatorsUserNameToFullName = new HashMap<>();
    
    public Layer1ApiMovingAverage(Layer1ApiProvider provider) {
        this.provider = provider;
        ListenableHelper.addListeners(provider, this);
    }
    
    @Override
    public void finish() {
        synchronized (indicatorsFullNameToUserName) {
            for (String userName : indicatorsFullNameToUserName.values()) {
                provider.sendUserMessage(new Layer1ApiUserMessageModifyIndicator(
                    Layer1ApiMovingAverage.class, userName, false));
            }
        }
        invalidateInterfaceMap.clear();
    }
    
    @Override
    public void onUserMessage(Object data) {
        if (data instanceof UserMessageLayersChainCreatedTargeted) {
            UserMessageLayersChainCreatedTargeted message = (UserMessageLayersChainCreatedTargeted) data;
            if (message.targetClass == getClass()) {
                provider.sendUserMessage(new Layer1ApiDataInterfaceRequestMessage(
                    dataStructureInterface -> this.dataStructureInterface = dataStructureInterface));
            }
        }
    }
    
    @Override
    public void onInstrumentAdded(String alias, InstrumentInfo instrumentInfo) {
        MovingAverageSettings settings = settingsMap.computeIfAbsent(alias, k -> new MovingAverageSettings());
        
        String indicatorName = INDICATOR_NAME + "_" + alias;
        
        synchronized (indicatorsFullNameToUserName) {
            indicatorsFullNameToUserName.put(alias, indicatorName);
            indicatorsUserNameToFullName.put(indicatorName, alias);
        }
        
        Layer1ApiUserMessageModifyIndicator message = getUserMessageAdd(
            indicatorName, 
            settings.color,
            true
        );
        
        provider.sendUserMessage(message);
    }
    
    @Override
    public void onInstrumentRemoved(String alias) {
        synchronized (indicatorsFullNameToUserName) {
            String userName = indicatorsFullNameToUserName.remove(alias);
            if (userName != null) {
                indicatorsUserNameToFullName.remove(userName);
                provider.sendUserMessage(new Layer1ApiUserMessageModifyIndicator(
                    Layer1ApiMovingAverage.class, userName, false));
            }
        }
        
        InvalidateInterface invalidateInterface = invalidateInterfaceMap.remove(alias);
        if (invalidateInterface != null) {
            invalidateInterface.invalidate();
        }
        
        settingsMap.remove(alias);
    }
    
    @Override
    public void onInstrumentNotFound(String symbol, String exchange, String type) {}
    
    @Override
    public void onInstrumentAlreadySubscribed(String symbol, String exchange, String type) {}
    
    private Layer1ApiUserMessageModifyIndicator getUserMessageAdd(String userName,
            Color color, boolean isAddWidget) {
        return Layer1ApiUserMessageModifyIndicator.builder(Layer1ApiMovingAverage.class, userName)
            .setIsAdd(true)
            .setGraphType(GraphType.PRIMARY)
            .setOnlineCalculatable(this)
            .setIndicatorColorScheme(new IndicatorColorScheme() {
                @Override
                public ColorDescription[] getColors() {
                    return new ColorDescription[] {
                        new ColorDescription(Layer1ApiMovingAverage.class, COLOR_NAME, color, false)
                    };
                }
                
                @Override
                public String getColorFor(Double value) {
                    return COLOR_NAME;
                }
                
                @Override
                public ColorIntervalResponse getColorIntervalsList(double valueFrom, double valueTo) {
                    return new ColorIntervalResponse(new String[] {COLOR_NAME}, new double[] {});
                }
            })
            .setIndicatorLineStyle(IndicatorLineStyle.NONE)
            .build();
    }
    
    @Override
    public void addColorChangeListener(ColorsChangedListener listener) {
        // No-op for now, could be implemented for dynamic color changes
    }
    
    @Override
    public Color getColor(String alias, String name) {
        MovingAverageSettings settings = settingsMap.get(alias);
        if (settings != null && COLOR_NAME.equals(name)) {
            return settings.color;
        }
        return null;
    }
    
    @Override
    public void setColor(String alias, String name, Color color) {
        MovingAverageSettings settings = settingsMap.get(alias);
        if (settings != null && COLOR_NAME.equals(name)) {
            settings.color = color;
        }
    }
    
    public Map<String, String> getIndicatorColorSchemeDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put(COLOR_NAME, "Moving Average Line");
        return descriptions;
    }
    
    @Override
    public void calculateValuesInRange(String indicatorName, String indicatorAlias, long t0, 
            long intervalWidth, int intervalsNumber, CalculatedResultListener listener) {
        
        if (dataStructureInterface == null) {
            listener.setCompleted();
            return;
        }
        
        String alias = indicatorsUserNameToFullName.get(indicatorName);
        if (alias == null) {
            listener.setCompleted();
            return;
        }
        
        MovingAverageSettings settings = settingsMap.get(alias);
        if (settings == null) {
            listener.setCompleted();
            return;
        }
        
        // Get trade data
        var result = dataStructureInterface.get(
            Layer1ApiMovingAverage.class,
            DataStructureInterface.StandardEvents.TRADE.toString(),
            t0,
            intervalWidth,
            intervalsNumber + settings.period,
            indicatorAlias,
            new Class<?>[] { TradeInfo.class }
        );
        
        // Calculate MA values
        MovingAverageCalculator calculator = new MovingAverageCalculator(settings);
        
        for (int i = 0; i < intervalsNumber; i++) {
            if (i < result.size()) {
                var interval = result.get(i);
                Object tradeObj = interval.events.get(TradeInfo.class.toString());
                
                if (tradeObj instanceof TradeInfo) {
                    TradeInfo trade = (TradeInfo) tradeObj;
                    // TradeInfo doesn't have a price field, get it from onTrade callback
                    listener.provideResponse(Double.NaN);
                } else {
                    listener.provideResponse(Double.NaN);
                }
            } else {
                listener.provideResponse(Double.NaN);
            }
        }
        
        listener.setCompleted();
    }
    
    @Override
    public OnlineValueCalculatorAdapter createOnlineValueCalculator(String indicatorName, 
            String indicatorAlias, long time, Consumer<Object> listener, 
            InvalidateInterface invalidateInterface) {
        
        String alias = indicatorsUserNameToFullName.get(indicatorName);
        if (alias != null) {
            invalidateInterfaceMap.put(alias, invalidateInterface);
        }
        
        MovingAverageSettings settings = settingsMap.get(alias);
        if (settings == null) {
            settings = new MovingAverageSettings();
        }
        
        MovingAverageCalculator calculator = new MovingAverageCalculator(settings);
        
        return new OnlineValueCalculatorAdapter() {
            @Override
            public void onTrade(String alias, double price, int size, TradeInfo tradeInfo) {
                if (alias.equals(indicatorAlias)) {
                    double maValue = calculator.addPrice(price);
                    listener.accept(maValue);
                }
            }
        };
    }
    
    /**
     * Helper class to calculate moving averages
     */
    private static class MovingAverageCalculator {
        private final MovingAverageSettings settings;
        private final Queue<Double> priceQueue;
        private double sum = 0.0;
        private double ema = Double.NaN;
        private final double multiplier;
        
        public MovingAverageCalculator(MovingAverageSettings settings) {
            this.settings = settings;
            this.priceQueue = new LinkedList<>();
            this.multiplier = 2.0 / (settings.period + 1.0);
        }
        
        public double addPrice(double price) {
            if (Double.isNaN(price)) {
                return Double.NaN;
            }
            
            switch (settings.maType) {
                case SMA:
                    return calculateSMA(price);
                case EMA:
                    return calculateEMA(price);
                case WMA:
                    return calculateWMA(price);
                default:
                    return calculateSMA(price);
            }
        }
        
        private double calculateSMA(double price) {
            priceQueue.offer(price);
            sum += price;
            
            if (priceQueue.size() > settings.period) {
                double oldPrice = priceQueue.poll();
                sum -= oldPrice;
            }
            
            if (priceQueue.size() < settings.period) {
                return Double.NaN;
            }
            
            return sum / settings.period;
        }
        
        private double calculateEMA(double price) {
            priceQueue.offer(price);
            
            if (priceQueue.size() < settings.period) {
                // Calculate SMA for initialization
                sum += price;
                if (priceQueue.size() == settings.period) {
                    ema = sum / settings.period;
                }
                return Double.NaN;
            }
            
            if (priceQueue.size() > settings.period) {
                priceQueue.poll();
            }
            
            // EMA formula: EMA = (Price - PreviousEMA) * multiplier + PreviousEMA
            ema = (price - ema) * multiplier + ema;
            return ema;
        }
        
        private double calculateWMA(double price) {
            priceQueue.offer(price);
            
            if (priceQueue.size() > settings.period) {
                priceQueue.poll();
            }
            
            if (priceQueue.size() < settings.period) {
                return Double.NaN;
            }
            
            // WMA calculation: sum of (price * weight) / sum of weights
            double weightedSum = 0.0;
            double weightSum = 0.0;
            int weight = 1;
            
            for (Double p : priceQueue) {
                weightedSum += p * weight;
                weightSum += weight;
                weight++;
            }
            
            return weightedSum / weightSum;
        }
    }
}
