package com.segment.analytics.android.integrations.adjust;

import android.app.Activity;
import android.content.Context;
import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustAttribution;
import com.adjust.sdk.AdjustConfig;
import com.adjust.sdk.AdjustEvent;
import com.adjust.sdk.AdjustInstance;
import com.adjust.sdk.LogLevel;
import com.adjust.sdk.OnAttributionChangedListener;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.integrations.IdentifyPayload;
import com.segment.analytics.integrations.Integration;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.integrations.TrackPayload;
import java.util.Map;

import static com.adjust.sdk.AdjustConfig.ENVIRONMENT_PRODUCTION;
import static com.adjust.sdk.AdjustConfig.ENVIRONMENT_SANDBOX;
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

  AdjustIntegration(ValueMap settings, final Analytics analytics) {
    this.adjust = Adjust.getDefaultInstance();
    this.logger = analytics.logger(ADJUST_KEY);
    this.customEvents = settings.getValueMap("customEvents");

    Context context = analytics.getApplication();
    String appToken = settings.getString("appToken");
    boolean setEnvironmentProduction = settings.getBoolean("setEnvironmentProduction", false);
    String environment = setEnvironmentProduction ? ENVIRONMENT_PRODUCTION : ENVIRONMENT_SANDBOX;
    AdjustConfig adjustConfig = new AdjustConfig(context, appToken, environment);
    boolean setEventBufferingEnabled = settings.getBoolean("setEventBufferingEnabled", false);
    if (setEventBufferingEnabled) {
      adjustConfig.setEventBufferingEnabled(true);
    }
    boolean trackAttributionData = settings.getBoolean("trackAttributionData", false);
    if (trackAttributionData) {
      OnAttributionChangedListener listener = new SegmentAttributionChangedListener(analytics);
      adjustConfig.setOnAttributionChangedListener(listener);
    }
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

  @Override public void identify(IdentifyPayload identify) {
    super.identify(identify);

    String userId = identify.userId();
    if (userId != null) {
      adjust.addSessionPartnerParameter("userId", userId);
      logger.verbose("adjust.addSessionPartnerParameter(userId, (%s))", userId);
    }

    String anonymousId = identify.anonymousId();
    if (anonymousId != null) {
      adjust.addSessionPartnerParameter("anonymousId", anonymousId);
      logger.verbose("adjust.addSessionPartnerParameter(anonymousId, (%s))", anonymousId);
    }
  }

  @Override public void reset() {
    super.reset();
    adjust.resetSessionPartnerParameters();
    logger.verbose("Adjust.getDefaultInstance().resetSessionPartnerParameters();");
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

  @Override public void onActivityResumed(Activity activity) {
    super.onActivityResumed(activity);

    adjust.onResume();
  }

  @Override public void onActivityPaused(Activity activity) {
    super.onActivityPaused(activity);

    adjust.onPause();
  }

  static class SegmentAttributionChangedListener implements OnAttributionChangedListener {
    final Analytics analytics;

    SegmentAttributionChangedListener(Analytics analytics) {
      this.analytics = analytics;
    }

    @Override public void onAttributionChanged(AdjustAttribution attribution) {
      Map<String, Object> campaign = new ValueMap() //
          .putValue("source", attribution.network)
          .putValue("name", attribution.campaign)
          .putValue("content", attribution.clickLabel)
          .putValue("adCreative", attribution.creative)
          .putValue("adGroup", attribution.adgroup);

      analytics.track("Install Attributed", new Properties() //
          .putValue("provider", "Adjust") //
          .putValue("trackerToken", attribution.trackerToken)
          .putValue("trackerName", attribution.trackerName)
          .putValue("campaign", campaign));
    }
  }
}
