package com.segment.analytics.android.integrations.adjust;

import android.app.Application;
import com.adjust.sdk.Adjust;
import com.adjust.sdk.AdjustConfig;
import com.adjust.sdk.AdjustEvent;
import com.adjust.sdk.AdjustInstance;
import com.adjust.sdk.LogLevel;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;
import com.segment.analytics.ValueMap;
import com.segment.analytics.core.tests.BuildConfig;
import com.segment.analytics.integrations.Logger;
import com.segment.analytics.test.TrackPayloadBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static com.segment.analytics.Analytics.LogLevel.NONE;
import static com.segment.analytics.Analytics.LogLevel.VERBOSE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(RobolectricGradleTestRunner.class)
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
        .putValue("setEnvironmentProduction", true);

    PowerMockito.whenNew(AdjustConfig.class)
        .withArguments(application, "foo", AdjustConfig.ENVIRONMENT_PRODUCTION)
        .thenReturn(config);

    AdjustIntegration.FACTORY.create(settings, analytics);

    verify(config).setEventBufferingEnabled(true);
    verify(config).setLogLevel(LogLevel.VERBOSE);
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
    verify(adjustInstance).onCreate(config);
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
}
