package de.lehrbaum.voiry

import ca.gosyer.appdirs.AppDirs
import de.lehrbaum.voiry.api.v1.TranscriptionStatus
import de.lehrbaum.voiry.api.v1.VoiceDiaryEntry
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import yairm210.purity.annotations.Pure

private const val STATUS_NAME_MAX_LENGTH = 20 // Maximum TranscriptionStatus name length

/**
 * Repository handling persistence of diary entries and their audio files.
 */
@ExperimentalUuidApi
@ExperimentalTime
class DiaryRepository(private val baseDir: Path) {
	private val database: Database

	init {
		Files.createDirectories(baseDir)
		val dbFile = baseDir.resolve("entries.sqlite")
		database = Database.connect("jdbc:sqlite:${dbFile.toAbsolutePath()}")
		transaction(database) { SchemaUtils.create(Entries) }
	}

	private object Entries : Table("entries") {
		val id = uuid("id")
		val title = text("title")
		val recordedAt = long("recorded_at")
		val duration = long("duration_ms")
		val transcriptionText = text("transcription_text").nullable()
		val transcriptionStatus = enumerationByName(
			"transcription_status",
			STATUS_NAME_MAX_LENGTH,
			TranscriptionStatus::class,
		)
		val transcriptionUpdatedAt = long("transcription_updated_at").nullable()
		override val primaryKey = PrimaryKey(id)
	}

	suspend fun add(entry: VoiceDiaryEntry, audio: ByteArray) {
		val id = UUID.fromString(entry.id.toString())
		val audioFile = baseDir.resolve("${entry.id}.wav")
		withContext(Dispatchers.IO) { Files.write(audioFile, audio) }
		newSuspendedTransaction(db = database) {
			Entries.insert {
				it[Entries.id] = id
				it[title] = entry.title
				it[recordedAt] = entry.recordedAt.toEpochMilliseconds()
				it[duration] = entry.duration.inWholeMilliseconds
				it[transcriptionText] = entry.transcriptionText
				it[transcriptionStatus] = entry.transcriptionStatus
				it[transcriptionUpdatedAt] = entry.transcriptionUpdatedAt?.toEpochMilliseconds()
			}
		}
	}

	suspend fun updateTranscription(
		id: Uuid,
		text: String?,
		status: TranscriptionStatus,
		updatedAt: Instant?,
	) {
		val uuid = UUID.fromString(id.toString())
		newSuspendedTransaction(db = database) {
			Entries.update({ Entries.id eq uuid }) {
				it[transcriptionText] = text
				it[transcriptionStatus] = status
				it[transcriptionUpdatedAt] = updatedAt?.toEpochMilliseconds()
			}
		}
	}

	suspend fun delete(id: Uuid) {
		val uuid = UUID.fromString(id.toString())
		newSuspendedTransaction(db = database) {
			Entries.deleteWhere { Entries.id eq uuid }
		}
		val audioFile = baseDir.resolve("$id.wav")
		withContext(Dispatchers.IO) { Files.deleteIfExists(audioFile) }
	}

	suspend fun getAudio(id: Uuid): ByteArray? {
		val audioFile = baseDir.resolve("$id.wav")
		if (!audioFile.exists()) return null
		return withContext(Dispatchers.IO) { Files.readAllBytes(audioFile) }
	}

	suspend fun getAll(): List<VoiceDiaryEntry> =
		newSuspendedTransaction(db = database) {
			Entries.selectAll().map {
				VoiceDiaryEntry(
					id = Uuid.parse(it[Entries.id].toString()),
					title = it[Entries.title],
					recordedAt = it[Entries.recordedAt].epochMillisToInstant(),
					duration = it[Entries.duration].milliseconds,
					transcriptionText = it[Entries.transcriptionText],
					transcriptionStatus = it[Entries.transcriptionStatus],
					transcriptionUpdatedAt = it[Entries.transcriptionUpdatedAt]?.epochMillisToInstant(),
				)
			}
		}

	companion object {
		fun create(): DiaryRepository {
			val env = System.getenv("VOICE_DIARY_DB_PATH")
			val dir = if (!env.isNullOrBlank()) {
				Path.of(env)
			} else {
				val appDirs = AppDirs { appName = "voice-diary" }
				Path.of(appDirs.getUserDataDir())
			}
			return DiaryRepository(dir)
		}
	}
}

@ExperimentalTime
@Pure
private fun Long.epochMillisToInstant() = Instant.fromEpochMilliseconds(this)
