package com.uykutakip.data

import android.app.*
import android.content.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    fun schedule(hour: Int, minute: Int, requestCode: Int, title: String, message: String) {
        val calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1) }
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("title", title).putExtra("message", message)
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
    }
    fun scheduleOnce(day: LocalDate, hour: Int, minute: Int, requestCode: Int, title: String, message: String) {
        val triggerAt = day.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) return
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("title", title).putExtra("message", message)
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }
    fun cancel(requestCode: Int) { val pending = PendingIntent.getBroadcast(context, requestCode, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); alarmManager.cancel(pending) }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Günlük takip hatırlatıcıları", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(intent.getStringExtra("title")).setContentText(intent.getStringExtra("message")).setAutoCancel(true).build()
        manager.notify(intent.hashCode(), notification)
    }
    private companion object { const val CHANNEL = "tracker_reminders" }
}
