package com.secondmemory.data.drive

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.client.http.AbstractInputStreamContent
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.secondmemory.domain.model.DriveSyncReport
import com.secondmemory.util.dailyDirectory
import com.secondmemory.util.monthlyDirectory
import com.secondmemory.util.rawDirectory
import com.secondmemory.util.weeklyDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Syncs app data bidirectionally with Google Drive using the official Drive SDK.
 *
 * Sync behavior:
 * - Files only on local device are uploaded to Drive.
 * - Files only on Drive are downloaded to local device.
 * - When files exist on both sides, the newer timestamp (with a small skew tolerance) wins.
 * - Newer remote files create a local backup before overwriting.
 * - Remote files are never deleted based on local device state.
 */
class GoogleDriveSyncClient(
    private val context: Context,
) {
    /**
     * Syncs local app files and remote Drive files bidirectionally for the connected Google account.
     * Downloads remote-only files, uploads local-only files, and resolves conflicts by timestamp.
     */
    suspend fun syncLocalDataTree(accountEmail: String?): DriveSyncReport = withContext(Dispatchers.IO) {        val selectedEmail = accountEmail?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Connect a Google account before syncing.")

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_FILE)))
            .setAccount(Account(selectedEmail, "com.google"))
            .build()
        
        val authorizationResult = Tasks.await(
            Identity.getAuthorizationClient(context).authorize(request)
        )

        val accessToken = authorizationResult.accessToken
            ?: throw IllegalStateException("Failed to obtain Drive access token.")

        val driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance()
        ) { httpRequest ->
            httpRequest.headers.authorization = "Bearer $accessToken"
        }.setApplicationName(APP_NAME).build()

        val rootFolderId = ensureFolder(driveService, null, ROOT_FOLDER_NAME)
        val dataFolderId = ensureFolder(driveService, rootFolderId, DATA_FOLDER_NAME)

        var uploaded = 0
        var downloaded = 0
        var deleted = 0
        var conflicted = 0

        LocalMirrorFolders.entries.forEach { entry ->
            val localDir = entry.localDirectory(context)
            localDir.mkdirs()

            val remoteFolderId = ensureFolder(driveService, dataFolderId, entry.remoteName)
            val localFiles = localDir.listFiles { file -> file.isFile }?.associateBy { it.name }.orEmpty()
            val remoteFiles = listFilesInFolder(driveService, remoteFolderId)
            val remoteByName = remoteFiles.associateBy { it.name }

            val localNames = localFiles.keys
            val remoteNames = remoteByName.keys

            (localNames - remoteNames).forEach { name ->
                createAndUploadFile(driveService, remoteFolderId, localFiles.getValue(name))
                uploaded += 1
            }

            (remoteNames - localNames).forEach { name ->
                val localFile = java.io.File(localDir, name)
                val remoteFile = remoteByName.getValue(name)
                downloadRemoteFile(
                    driveService,
                    remoteFile.id,
                    localFile,
                    remoteFile.modifiedTime?.value ?: System.currentTimeMillis()
                )
                downloaded += 1
            }

            (localNames intersect remoteNames).forEach { name ->
                val localFile = localFiles.getValue(name)
                val remoteFile = remoteByName.getValue(name)
                val remoteModified = remoteFile.modifiedTime?.value ?: 0L
                val localModified = localFile.lastModified()

                when {
                    localModified > remoteModified + TIME_SKEW_MILLIS -> {
                        updateRemoteFileContent(driveService, remoteFile.id, localFile)
                        uploaded += 1
                    }
                    remoteModified > localModified + TIME_SKEW_MILLIS -> {
                        // Conflict check: if remote was updated after local was last touched
                        // AND they are different, back up local before overwriting.
                        // However, we just set local modified to remote modified on last sync,
                        // so this branch should only trigger if remote really changed.
                        backupConflictedFile(localFile)
                        conflicted += 1
                        downloadRemoteFile(driveService, remoteFile.id, localFile, remoteModified)
                        downloaded += 1
                    }
                    else -> {
                        // Timestamps match within skew. No-op.
                        // Force sync timestamps just in case they are drifting within the skew
                        localFile.setLastModified(remoteModified)
                    }
                }
            }
        }

        DriveSyncReport(
            uploadedCount = uploaded,
            downloadedCount = downloaded,
            deletedCount = deleted,
            conflictedCount = conflicted,
            rootFolderId = rootFolderId,
        )
    }

    /**
     * Ensures a folder exists under the parent and returns its Drive id.
     */
    private fun ensureFolder(driveService: Drive, parentId: String?, folderName: String): String {
        val existing = findFolder(driveService, parentId, folderName)
        if (existing != null) return existing.id

        val metadata = File().apply {
            name = folderName
            mimeType = FOLDER_MIME_TYPE
            parents = if (parentId == null) null else listOf(parentId)
        }

        val created = driveService.files().create(metadata)
            .setFields("id,name")
            .execute()

        return created.id ?: throw IOException("Failed to create folder $folderName")
    }

    /**
     * Finds a folder by name under a parent folder.
     */
    private fun findFolder(driveService: Drive, parentId: String?, folderName: String): File? {
        val parentCondition = if (parentId == null) {
            "'root' in parents"
        } else {
            "'$parentId' in parents"
        }
        val query = "mimeType='$FOLDER_MIME_TYPE' and trashed=false and $parentCondition and name='${escapeQueryValue(folderName)}'"
        return driveService.files().list()
            .setQ(query)
            .setFields("files(id,name,mimeType,modifiedTime)")
            .setPageSize(10)
            .execute()
            .files
            ?.firstOrNull()
    }

    /**
     * Lists non-folder files inside a given Drive folder.
     */
    private fun listFilesInFolder(driveService: Drive, folderId: String): List<File> {
        val query = "'$folderId' in parents and trashed=false and mimeType!='${FOLDER_MIME_TYPE}'"
        val listResponse: FileList = driveService.files().list()
            .setQ(query)
            .setFields("files(id,name,mimeType,modifiedTime)")
            .setPageSize(1000)
            .execute()

        return listResponse.files ?: emptyList()
    }

    /**
     * Creates a file record and uploads local bytes as content.
     * Sets the remote modified time to match the local file's timestamp to prevent false conflicts.
     */
    private fun createAndUploadFile(driveService: Drive, parentFolderId: String, localFile: java.io.File) {
        val metadata = File().apply {
            name = localFile.name
            mimeType = localFile.mimeType()
            parents = listOf(parentFolderId)
            modifiedTime = DateTime(localFile.lastModified())
        }

        val created = driveService.files().create(metadata, contentFor(localFile))
            .setFields("id,modifiedTime")
            .execute()

        // Sync local timestamp with exact server time to be safe
        val serverTime = created.modifiedTime?.value
        if (serverTime != null) {
            localFile.setLastModified(serverTime)
        }
    }

    /**
     * Updates remote file contents with local file bytes.
     * Sets the remote modified time to match the local file's timestamp.
     */
    private fun updateRemoteFileContent(driveService: Drive, remoteId: String, localFile: java.io.File) {
        val metadata = File().apply {
            modifiedTime = DateTime(localFile.lastModified())
        }
        val request = driveService.files().update(remoteId, metadata, contentFor(localFile))
        // Use generic set to bypass potentially missing typed setter in some library versions
        request.set("setModifiedTime", true)
        val updated = request.setFields("id,modifiedTime").execute()

        // Sync local timestamp with exact server time
        val serverTime = updated.modifiedTime?.value
        if (serverTime != null) {
            localFile.setLastModified(serverTime)
        }
    }

    /**
     * Downloads a remote file and writes it to local storage.
     * Sets the local file's last modified time to match the remote file's modified time to avoid false conflicts.
     */
    private fun downloadRemoteFile(
        driveService: Drive,
        remoteId: String,
        localFile: java.io.File,
        remoteModified: Long
    ) {
        val output = java.io.ByteArrayOutputStream()
        driveService.files().get(remoteId).executeMediaAndDownloadTo(output)
        localFile.parentFile?.mkdirs()
        localFile.writeBytes(output.toByteArray())
        // Set last modified time to match remote
        localFile.setLastModified(remoteModified)
    }

    /**
     * Deletes a remote file by id.
     */
    private fun deleteRemoteFile(driveService: Drive, remoteId: String) {
        driveService.files().delete(remoteId).execute()
    }

    /**
     * Creates a local backup when a newer remote version overwrites local content.
     */
    private fun backupConflictedFile(localFile: java.io.File) {
        val backup = java.io.File(
            localFile.parentFile,
            "${localFile.nameWithoutExtension}.conflict.${System.currentTimeMillis()}.${localFile.extension}",
        )
        localFile.copyTo(backup, overwrite = true)
    }

    /**
     * Converts local files into Drive SDK upload content.
     */
    private fun contentFor(localFile: java.io.File): AbstractInputStreamContent {
        return when (localFile.extension.lowercase()) {
            "json", "md" -> ByteArrayContent(localFile.mimeType(), localFile.readBytes())
            else -> FileContent(localFile.mimeType(), localFile)
        }
    }

    /**
     * Escapes single quotes for Drive query strings.
     */
    private fun escapeQueryValue(value: String): String = value.replace("'", "\\'")

    /**
     * Infers MIME type for supported local file formats.
     */
    private fun java.io.File.mimeType(): String {
        return when (extension.lowercase()) {
            "json" -> "application/json"
            "md" -> "text/markdown"
            else -> "application/octet-stream"
        }
    }

    /**
     * Maps local app folders to remote subfolders in Drive.
     */
    private enum class LocalMirrorFolders(
        val remoteName: String,
        val localDirectory: (Context) -> java.io.File,
    ) {
        RAW("raw", ::rawDirectory),
        DAILY("daily", ::dailyDirectory),
        WEEKLY("weekly", ::weeklyDirectory),
        MONTHLY("monthly", ::monthlyDirectory),
    }

    private companion object {
        const val APP_NAME = "SecondMemory"
        const val ROOT_FOLDER_NAME = "com.secondmemory"
        const val DATA_FOLDER_NAME = "data"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val TIME_SKEW_MILLIS = 10_000L
    }
}
