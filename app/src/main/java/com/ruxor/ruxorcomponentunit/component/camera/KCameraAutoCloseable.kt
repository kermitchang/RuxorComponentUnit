package com.ruxor.ruxorcomponentunit.component.camera

class KCameraAutoCloseable<T : AutoCloseable?>(`object`: T?) : AutoCloseable {
    private var mObject: T?
    private var mRefCount: Long = 0

    /**
     * Wrap the given object.
     *
     * @param object an object to wrap.
     */
    init {
        if (`object` == null) throw NullPointerException()
        mObject = `object`
    }

    @get:Synchronized
    val andRetain: T?
        /**
         * Increment the reference count and return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        get() {
            if (mRefCount < 0) {
                return null
            }
            mRefCount++
            return mObject
        }

    /**
     * Return the wrapped object.
     *
     * @return the wrapped object, or null if the object has been released.
     */
    @Synchronized
    fun get(): T? {
        return mObject
    }

    /**
     * Decrement the reference count and release the wrapped object if there are no other
     * users retaining this object.
     */
    @Synchronized
    override fun close() {
        if (mRefCount >= 0) {
            mRefCount--
            if (mRefCount < 0) {
                try {
                    mObject!!.close()
                } catch (e: java.lang.Exception) {
                    throw RuntimeException(e)
                } finally {
                    mObject = null
                }
            }
        }
    }
}