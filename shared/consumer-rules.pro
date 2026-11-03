# Caught failures are recorded through Crashlytics by reflection (see NonFatalReporting.kt in
# :shared), so the app builds without Firebase. Keep the two members it looks up.
-keep class com.google.firebase.crashlytics.FirebaseCrashlytics {
    public static com.google.firebase.crashlytics.FirebaseCrashlytics getInstance();
    public void recordException(java.lang.Throwable);
}
