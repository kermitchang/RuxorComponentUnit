package com.ruxor.ruxorcomponentunit.uiview.viewflipper

import android.content.Context
import android.media.MediaRecorder
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.widget.ViewFlipper
import androidx.core.view.children
import com.ruxor.ruxorcomponentunit.R
import java.io.File
import java.util.concurrent.ScheduledExecutorService
import kotlin.math.abs
import kotlin.random.Random.Default.nextInt

class KViewFlipper(context: Context) : ViewFlipper(context) {

    private val ACTION_SHOW_PREVIOUS = "action.show.previous"
    private val ACTION_SHOW_NEXT = "action.show.next"
    private val DEFAULT_CYCLE_ENABLED = true
    private val SWIPE_HORIZON = "swipe.horizon"
    private val SWIPE_THRESHOLD = 50
    private val SWIPE_VELOCITY_THRESHOLD = 50
    private val SWIPE_VERTICAL = "swipe.vertical"
    private val TAG = "PTVerticalViewFlipper"

    private val context = context
    private var cycleEnable = this.DEFAULT_CYCLE_ENABLED
    private val gestureListener = object : OnGestureListener{
        override fun onDown(e: MotionEvent): Boolean { return true }

        override fun onShowPress(e: MotionEvent) {}

        override fun onSingleTapUp(e: MotionEvent): Boolean {return true}

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean { return true }

        override fun onLongPress(e: MotionEvent) {}

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            var result = false
            try {
                val diffY = e2.y - e1?.y!!
                val diffX = e2.x - e1?.x!!
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) this@KViewFlipper.onSwipeRight()
                        else this@KViewFlipper.onSwipeLeft()
                        result = true
                    }
                } else if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) this@KViewFlipper.onSwipeBottom()
                    else this@KViewFlipper.onSwipeTop()
                    result = true
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
            return result
        }

    }
    private val gestureDetector = GestureDetector(this.context, this.gestureListener)
    private var viewFlipperCallback: KViewFlipperCallback?= null
    private val animationListener= object:AnimationListener{
        override fun onAnimationStart(animation: Animation?) {
        }

        override fun onAnimationEnd(animation: Animation?) {
        }

        override fun onAnimationRepeat(animation: Animation?) {
        }

    }

    /**
     * Test auto slides
     * **/
    private val mHandler1: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when(msg.what) {
                0 -> this@KViewFlipper.onSwipeBottom()
                1 -> this@KViewFlipper.onSwipeLeft()
                2 -> this@KViewFlipper.onSwipeRight()
                else -> this@KViewFlipper.onSwipeTop()
            }
        }
    }

    private var timerExecutor: ScheduledExecutorService?= null
    private val timeRunnableTask = Runnable {
        val value = nextInt(0, 4)
        val message = Message()
        message.what = value
        this.mHandler1.sendMessage(message)
    }
    private val timerDuration: Long = 500

    public fun onSwipeBottom() {
        Log.i(TAG,"onSwipeBottom")
        if ( (!this.cycleEnable && (this.displayedChild - 1 < 0)) || this.childCount == 1) return

        this.setInAnimation(this.context, R.anim.top_in)
        this.setOutAnimation(this.context, R.anim.top_out)
        this.inAnimation.setAnimationListener(this.animationListener)

        this.showPrevious()
    }

    public fun onSwipeLeft() {
        Log.i(TAG,"onSwipeLeft")

        val viewFlipper = this.getChildAt(this.displayedChild) as ViewFlipper
        if ((!this.cycleEnable && (viewFlipper.displayedChild + 1 >= viewFlipper.childCount)) || viewFlipper.childCount == 1)
            return

        viewFlipper.setInAnimation(this.context, R.anim.right_in)
        viewFlipper.setOutAnimation(this.context, R.anim.right_out)
        viewFlipper.inAnimation.setAnimationListener(this.animationListener)

        viewFlipper.showNext()
    }

    public fun onSwipeRight() {
        Log.i(TAG,"onSwipeRight")

        val viewFlipper = this.getChildAt(this.displayedChild) as ViewFlipper
        if ( (!this.cycleEnable && (viewFlipper.displayedChild - 1 < 0)) || viewFlipper.childCount == 1)
            return

        viewFlipper.setInAnimation(this.context, R.anim.left_in)
        viewFlipper.setOutAnimation(this.context, R.anim.left_out)
        viewFlipper.inAnimation.setAnimationListener(this.animationListener)
        viewFlipper.showPrevious()
    }

    public fun onSwipeTop() {
        Log.i(TAG,"onSwipeTop")
        if ( (!this.cycleEnable && (this.displayedChild + 1 >= this.childCount)) || this.childCount == 1) return

        this.setInAnimation(this.context, R.anim.bottom_in)
        this.setOutAnimation(this.context, R.anim.bottom_out)
        this.inAnimation.setAnimationListener(this.animationListener)
        this.showNext()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        val pointCount = event.pointerCount
        if (pointCount >= 5) {
            this.viewFlipperCallback?.onAction()
            return true
        } else {
            return this.gestureDetector.onTouchEvent(event)
        }
    }

    public fun release() {
        this.children.toList().forEach { view ->
            (view as ViewFlipper).removeAllViews()
        }
        this.removeAllViews()
    }

    public fun setCycleEnable(enabled: Boolean) {
        this.cycleEnable = enabled
    }

    public fun setViewFlipperCallback(viewFlipperCallback: KViewFlipperCallback) {
        this.viewFlipperCallback = viewFlipperCallback
    }

}