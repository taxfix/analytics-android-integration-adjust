package com.segment.analytics.android.integrations.adjust;

import android.content.Context;
import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustConfig;
import com.adjust.sdk.AdjustEvent;
import com.adjust.sdk.AdjustInstance;
import com.adjust.sdk.LogLevel;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;
import java.util.Map;

import static com.segment.analytics.internal.Utils.isNullOrEmpty;

/**
 * Adjust is a business intelligence platform for mobile app marketers,
 * combining attribution for advertising sources with analytics and store
 * statistics.
 *
 * @see <a href="https://www.adjust.com">Adjust</a>
 * @see <a href="https://segment.com/docs/integrations/adjust/">Adjust Integration</a>
 * @see <a href="https://github.com/adjust/android_sdk">Adjust Android SDK</a>
 */
public class AdjustIntegration extends Integration<AdjustInstance> {
  static final String ADJUST_KEY = "Adjust";
  public static final Factory FACTORY = new Factory() {
    @Override public Integration<?> create(ValueMap settings, Analytics analytics) {
      return new AdjustIntegration(settings, analytics);
    }

    @Override public String key() {
      return ADJUST_KEY;
    }
  };

  private final Logger logger;
  private final AdjustInstance adjust;
  private final ValueMap customEvents;

  AdjustIntegration(ValueMap settings, Analytics analytics) {
    this.adjust = Adjust.getDefaultInstance();
    this.logger = analytics.logger(ADJUST_KEY);
    this.customEvents = settings.getValueMap("customEvents");

    Context context = analytics.getApplication();
    String appToken = settings.getString("appToken");
    String environment = AdjustConfig.ENVIRONMENT_SANDBOX;
    boolean setEnvironmentProduction = settings.getBoolean("setEnvironmentProduction", false);
    if (setEnvironmentProduction) {
      environment = AdjustConfig.ENVIRONMENT_PRODUCTION;
    }
    AdjustConfig adjustConfig = new AdjustConfig(context, appToken, environment);
    switch (logger.logLevel) {
      case INFO:
        adjustConfig.setLogLevel(LogLevel.INFO);
        break;
      case DEBUG:
      case BASIC:
        adjustConfig.setLogLevel(LogLevel.DEBUG);
        break;
      case VERBOSE:
        adjustConfig.setLogLevel(LogLevel.VERBOSE);
        break;
      case NONE:
      default:
        break;
    }
    adjust.onCreate(adjustConfig);
  }

  @Override public AdjustInstance getUnderlyingInstance() {
    return adjust;
  }

  @Override public void track(TrackPayload track) {
    super.track(track);

    String token = customEvents.getString(track.event());
    if (isNullOrEmpty(token)) {
      return;
    }

    Properties properties = track.properties();
    AdjustEvent event = new AdjustEvent(token);
    for (Map.Entry<String, Object> entry : properties.entrySet()) {
      event.addCallbackParameter(entry.getKey(), String.valueOf(entry.getValue()));
    }
    double revenue = properties.revenue();
    String currency = properties.currency();
    if (revenue != 0 && !isNullOrEmpty(currency)) {
      event.setRevenue(revenue, currency);
    }

    logger.verbose("Adjust.getDefaultInstance().trackEvent(%s);", event);
    adjust.trackEvent(event);
  }
}
