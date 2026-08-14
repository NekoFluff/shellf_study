package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crazyfluff.shellfstudy.core.data.PitchAccentRepository
import com.crazyfluff.shellfstudy.shared.database.SubjectDao
import com.crazyfluff.shellfstudy.shared.database.pitchaccent.PitchAccentCacheDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

private const val BATCH_SIZE = 20
private val STALE_AFTER: Duration = Duration.ofDays(7)
private const val REQUEST_SPACING_MS = 750L

/**
 * Proactively fills in pitch-accent data for vocab words weblio hasn't been scraped for recently,
 * mirroring Smouldering Durtles' background `scheduleDownloadTasks` batching. Runs on a low-key
 * periodic schedule (see [PitchAccentScrapeScheduler]) rather than per-view, and rate-limits itself
 * with an explicit delay between requests since there's no shared task queue to lean on here.
 */
@HiltWorker
class PitchAccentScrapeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val subjectDao: SubjectDao,
    private val pitchAccentCacheDao: PitchAccentCacheDao,
    private val pitchAccentRepository: PitchAccentRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = Instant.now().toEpochMilli()
        val staleCutoff = now - STALE_AFTER.toMillis()

        val allVocab = subjectDao.getUnlockedVocabularyCharacters().toSet()
        val fresh = pitchAccentCacheDao.getFreshCharacters(staleCutoff).toSet()
        val candidates = (allVocab - fresh).take(BATCH_SIZE)

        candidates.forEachIndexed { index, characters ->
            pitchAccentRepository.scrapeAndCache(characters, now)
            if (index < candidates.lastIndex) delay(REQUEST_SPACING_MS)
        }

        return Result.success()
    }
}
