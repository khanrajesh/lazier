package com.velox.lazeir.utils

import android.annotation.SuppressLint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


/**
 * Author: [Rajesh Khan]
 * */
sealed class NetworkResource<T>(
    val data: T? = null, val message: String? = null, val errorObject: JSONObject? = null,val code:Int?= null
) {
    class Success<T>(data: T?) : NetworkResource<T>(data)

    class Error<T>(message: String?, errorObject: JSONObject? = null,code: Int?=null, data: T? = null,) :
        NetworkResource<T>(data, message, errorObject,code)

    class Loading<T>(val isLoading: Boolean) : NetworkResource<T>(null)

}

/**
 * [awaitHandler]
 * */
suspend fun <T> Call<T>.awaitHandler(): T = suspendCoroutine { continuation ->
    val callback = object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            continuation.resumeNormallyOrWithException {
                response.isSuccessful || throw IllegalStateException("Http error ${response.code()}")
                response.body() ?: throw IllegalStateException("Response body is null")
            }
        }

        override fun onFailure(call: Call<T>, t: Throwable) = continuation.resumeWithException(t)
    }
    enqueue(callback)
}


private inline fun <T> Continuation<T>.resumeNormallyOrWithException(getter: () -> T) = try {
    val result = getter()
    resume(result)
} catch (exception: Throwable) {
    resumeWithException(exception)
}


/**
 * [handleNetworkResponse] handle the API response,
 * convert the dto response to domain response
 * extracting the error according to the error code
 * **/
@SuppressLint("LogNotTimber")
fun <T, O> handleNetworkResponse(
    call: suspend () -> Response<T>, mapFun: (it: T) -> O
): Flow<NetworkResource<O>> {
    return flow {
        emit(NetworkResource.Loading(true))
        try {

            val response = call.invoke()
            val code = response.code()
            if (response.isSuccessful) {
                val data = response.body()?.let { mapFun(it) }
                emit(NetworkResource.Success(data))
            } else {
                val errorBody = response.errorBody()!!.string()
                try {
                    val jObjError = JSONObject(errorBody)
                    emit(NetworkResource.Error("Response Error", jObjError, code))
                } catch (e: Exception) {
                    emit(NetworkResource.Error("UNKNOWN ERROR", null,code))
                }

            }
        } catch (e: IOException) {
            e.message?.let { emit(NetworkResource.Error(it)) }
        } catch (e: HttpException) {
            e.message?.let { emit(NetworkResource.Error(it)) }
        } catch (e: IllegalStateException) {
            e.message?.let { emit(NetworkResource.Error(it)) }
        } catch (e: NullPointerException) {
            e.message?.let { emit(NetworkResource.Error(it)) }
        } catch (e: Exception) {
            e.message?.let { emit(NetworkResource.Error(it)) }
        }
        emit(NetworkResource.Loading(false))
    }
}

/**
 * [handleNetworkResponse] handle the API response,
 * extracting the error according to the error code
 * **/
@SuppressLint("LogNotTimber")
fun <T> handleNetworkResponse(response: Response<T>): Flow<NetworkResource<T>> {
    return flow {
        emit(NetworkResource.Loading(isLoading = true))
        try {
            val code = response.code()
            if (response.isSuccessful) {
                emit(NetworkResource.Success(response.body()))
            } else {
                val errorBody = response.errorBody()?.string()
                try {
                    val jObjError = errorBody?.let { JSONObject(it) }
                    emit(NetworkResource.Error("Network Error", jObjError,code))
                } catch (e: Exception) {
                    emit(NetworkResource.Error("UNKNOWN ERROR",code=code))
                }

            }
        } catch (e: IOException) {
            e.message?.let { emit(NetworkResource.Error(it)) }
        } catch (e: HttpException) {
            e.message?.let { emit(NetworkResource.Error(it)) }
        } catch (e: IllegalStateException) {
            e.message?.let { emit(NetworkResource.Error(it)) }
        } catch (e: NullPointerException) {
            e.message?.let { emit(NetworkResource.Error(it)) }
        } catch (e: Exception) {
            e.message?.let { emit(NetworkResource.Error(it)) }
        }
        emit(NetworkResource.Loading(isLoading = false))
    }
}


/**
 * [handleFlow] takes the response from use case function as Resource<> with in Main Coroutine Scope
 * return the extracted response with in onLoading(),onFailure(),onSuccess()
 * **/
fun <T> handleFlow(
    response: Flow<NetworkResource<T>>,
    onLoading: suspend (it: Boolean) -> Unit,
    onFailure: suspend (it: String?, errorObject: JSONObject?,code:Int?,data:T?) -> Unit,
    onSuccess: suspend (it: T) -> Unit
) {
    CoroutineScope(Dispatchers.Main).launch {
        response.collectLatest {
            when (it) {
                is NetworkResource.Error -> {
                    onFailure.invoke(it.message, it.errorObject,it.code,it.data)
                }
                is NetworkResource.Loading -> {
                    onLoading.invoke(it.isLoading)
                }
                is NetworkResource.Success -> {
                    onSuccess.invoke(it.data!!)
                }
            }
        }
    }
}
//cr velox






