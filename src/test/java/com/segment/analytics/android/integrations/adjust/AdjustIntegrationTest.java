package com.segment.analytics.android.integrations.adjust;

import android.app.Activity;
import android.app.Application;
import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustAttribution;
import com.adjust.sdk.AdjustConfig;
import com.adjust.sdk.AdjustEvent;
import com.adjust.sdk.AdjustInstance;
import com.adjust.sdk.LogLevel;
import com.adjust.sdk.OnAttributionChangedListener;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.Traits;
import com.segment.analytics.ValueMap;
import com.segment.analytics.android.integrations.adjust.AdjustIntegration.SegmentAttributionChangedListener;
import com.segment.analytics.core.tests.BuildConfig;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.test.TrackPayloadBuilder;
import com.segment.analytics.test.IdentifyPayloadBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.segment.analytics.Analytics.LogLevel.NONE;
import static com.segment.analytics.Analytics.LogLevel.VERBOSE;
import static com.segment.analytics.Utils.createTraits;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 18, manifest = Config.NONE)
@PowerMockIgnore({ "org.mockito.*", "org.robolectric.*", "android.*" })
@PrepareForTest({ Adjust.class, AdjustConfig.class, AdjustEvent.class, AdjustIntegration.class }) //
public class AdjustIntegrationTest {
  @Rule public PowerMockRule rule = new PowerMockRule();
  @Mock Application application;
  @Mock Analytics analytics;
  @Mock AdjustConfig config;
  @Mock AdjustInstance adjustInstance;
  AdjustIntegration integration;

  @Before public void setUp() {
    initMocks(this);

    when(analytics.logger("Adjust")).thenReturn(Logger.with(VERBOSE));
    ValueMap settings = new ValueMap() //
        .putValue("customEvents", new ValueMap().putValue("foo", "bar"));

    PowerMockito.mockStatic(Adjust.class);
    when(Adjust.getDefaultInstance()).thenReturn(adjustInstance);
    integration = new AdjustIntegration(settings, analytics);
    // Reset the mock post initialization for tests.
    PowerMockito.mockStatic(Adjust.class);
  }

  @Test public void initialize() throws Exception {
    PowerMockito.mockStatic(Adjust.class);
    when(analytics.getApplication()).thenReturn(application);
    when(analytics.logger("Adjust")).thenReturn(Logger.with(VERBOSE));
    when(Adjust.getDefaultInstance()).thenReturn(adjustInstance);
    ValueMap settings = new ValueMap() //
        .putValue("appToken", "foo") //
        .putValue("setEventBufferingEnabled", true) //
        .putValue("setEnvironmentProduction", true) //
        .putValue("trackAttributionData", true);

    PowerMockito.whenNew(AdjustConfig.class)
        .withArguments(application, "foo", AdjustConfig.ENVIRONMENT_PRODUCTION)
        .thenReturn(config);

    AdjustIntegration.FACTORY.create(settings, analytics);

    verify(config).setEventBufferingEnabled(true);
    verify(config).setLogLevel(LogLevel.VERBOSE);
    verify(config) //
        .setOnAttributionChangedListener(isA(SegmentAttributionChangedListener.class));

    verify(adjustInstance).onCreate(config);
  }

  @Test public void initializeWithDefaults() throws Exception {
    PowerMockito.mockStatic(Adjust.class);
    when(analytics.getApplication()).thenReturn(application);
    when(analytics.logger("Adjust")).thenReturn(Logger.with(NONE));
    when(Adjust.getDefaultInstance()).thenReturn(adjustInstance);
    ValueMap settings = new ValueMap().putValue("appToken", "foo");

    PowerMockito.whenNew(AdjustConfig.class)
        .withArguments(application, "foo", AdjustConfig.ENVIRONMENT_SANDBOX)
        .thenReturn(config);

    AdjustIntegration.FACTORY.create(settings, analytics);

    verify(config, never()).setEventBufferingEnabled(anyBoolean());
    verify(config, never()).setLogLevel(any(LogLevel.class));
    verify(config, never()) //
        .setOnAttributionChangedListener(any(OnAttributionChangedListener.class));
    verify(adjustInstance).onCreate(config);
  }

  @Test public void identifyWithAnonymousId() {
    PowerMockito.mockStatic(Adjust.class);
    Traits traits = createTraits() //
        .putValue("anonymousId", "1234")
        .putValue("firstName", "ladan");

    integration.identify(new IdentifyPayloadBuilder().traits(traits).build());

    verify(adjustInstance).addSessionPartnerParameter("anonymousId", "1234");
  }

  @Test public void identifyWithUserId() {
    PowerMockito.mockStatic(Adjust.class);
    Traits traits = createTraits("34235") //
        .putValue("firstName", "Prateek");

    integration.identify(new IdentifyPayloadBuilder().traits(traits).build());

    verify(adjustInstance).addSessionPartnerParameter("userId", "34235");
  }

  @Test public void identifyWithBoth() {
    PowerMockito.mockStatic(Adjust.class);
    Traits traits = createTraits("34235") //
        .putValue("anonymousId", "123")
        .putValue("firstName", "Prateek");

    integration.identify(new IdentifyPayloadBuilder().traits(traits).build());

    verify(adjustInstance).addSessionPartnerParameter("userId", "34235");
    verify(adjustInstance).addSessionPartnerParameter("anonymousId", "123");

  }

  @Test public void reset() {
    integration.reset();
    verify(adjustInstance).resetSessionPartnerParameters();
  }

  @Test public void track() throws Exception {
    AdjustEvent event = mock(AdjustEvent.class);
    PowerMockito.whenNew(AdjustEvent.class).withArguments("bar").thenReturn(event);

    integration.track(new TrackPayloadBuilder().event("foo").build());
    verify(adjustInstance).trackEvent(event);
  }

  @Test public void trackWithoutMatchingCustomEventDoesNothing() throws Exception {
    integration.track(new TrackPayloadBuilder().event("bar").build());
    verify(adjustInstance, never()).trackEvent(any(AdjustEvent.class));
  }

  @Test public void trackWithRevenue() throws Exception {
    AdjustEvent event = mock(AdjustEvent.class);
    PowerMockito.whenNew(AdjustEvent.class).withArguments("bar").thenReturn(event);

    integration.track(new TrackPayloadBuilder() //
        .event("foo") //
        .properties(new Properties().putRevenue(10.91).putCurrency("USD")) //
        .build());

    verify(event).setRevenue(10.91, "USD");

    verify(adjustInstance).trackEvent(event);
  }

  @Test public void trackWithProperties() throws Exception {
    AdjustEvent event = mock(AdjustEvent.class);
    PowerMockito.whenNew(AdjustEvent.class).withArguments("bar").thenReturn(event);

    integration.track(new TrackPayloadBuilder() //
        .event("foo") //
        .properties(new Properties().putValue("type", 1).putValue("category", "shirt")) //
        .build());

    verify(event).addCallbackParameter("type", "1");
    verify(event).addCallbackParameter("category", "shirt");
    verify(adjustInstance).trackEvent(event);
  }

  @Test public void trackAttributionData() {
    SegmentAttributionChangedListener listener = new SegmentAttributionChangedListener(analytics);
    AdjustAttribution data = new AdjustAttribution();
    data.network = "FB";
    data.campaign = "Campaign Name";
    data.clickLabel = "Organic Content Title";
    data.creative = "Red Hello World Ad";
    data.adgroup = "Red Ones";
    data.trackerToken = "foo";
    data.trackerName = "bar";

    listener.onAttributionChanged(data);

    verify(analytics).track("Install Attributed", new Properties() //
        .putValue("provider", "Adjust") //
        .putValue("trackerToken", "foo")
        .putValue("trackerName", "bar")
        .putValue("campaign", new ValueMap() //
            .putValue("source", "FB")
            .putValue("name", "Campaign Name")
            .putValue("content", "Organic Content Title")
            .putValue("adCreative", "Red Hello World Ad")
            .putValue("adGroup", "Red Ones")));
  }

  @Test public void onActivityResumed() {
    integration.onActivityResumed(mock(Activity.class));

    verify(adjustInstance).onResume();
  }

  @Test public void onActivityPaused() {
    integration.onActivityPaused(mock(Activity.class));

    verify(adjustInstance).onPause();
  }
}
