package com.monday8am.baseapp.data

import com.monday8am.baseapp.data.local.UserDatabase
import com.monday8am.baseapp.data.local.user.CachedUser
import com.monday8am.baseapp.data.remote.RemoteClient
import com.monday8am.baseapp.di.DefaultDispatcher
import com.monday8am.baseapp.domain.model.User
import com.monday8am.baseapp.domain.repo.Repository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class RepositoryImpl @Inject constructor(
    private val remoteDataSource: RemoteClient,
    private val database: UserDatabase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) : Repository, CoroutineScope {

    private val internalJob = Job()

    override fun getUsers(): Flow<List<User>> {
        launch {
            try {
                val isEmpty = database.userDao().getUsers().isEmpty()
                if (isEmpty) {
                    val users = remoteDataSource.getUsers()
                    database.userDao().insert(users.map { it.toCached() })
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        return database.userDao().getUsersFlow().map { cachedList -> cachedList.map { it.toUser() } }
    }

    override suspend fun addUser(user: User) {
        database.userDao().insert(user.toCached())
    }

    override suspend fun removeUser(userId: String) {
        database.userDao().delete(userId)
    }

    override suspend fun sort() {
        TODO("Not yet implemented")
    }

    private fun CachedUser.toUser(): User {
        return User(
            name = name,
            position = position,
            platform = platform,
            pic = pic
        )
    }

    private fun User.toCached(): CachedUser {
        return CachedUser(
            name = name,
            position = position,
            platform = platform,
            pic = pic,
            updatedAt = System.currentTimeMillis()
        )
    }

    override val coroutineContext: CoroutineContext
        get() = defaultDispatcher + internalJob
}
