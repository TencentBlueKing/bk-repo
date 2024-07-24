package com.tencent.bkrepo.common.api.thread

/**
 * 可传播的threadLocal
 *
 * 仅适用于线程池传播
 * 这个类是有必要的，因为在一些时候业务代码之外的一些框架可能会创建一些线程，做一些异步任务。
 * 一般情况下，即使传播到新的线程，也没什么关系，但是如果有一些特殊情况，比如对象比较大时，我们则不希望在非业务线程上传播对象。
 * */
open class TransmittableThreadLocal<T> : ThreadLocal<T>() {

    override fun set(value: T) {
        super.set(value)
        if (!Transmitter.holder.get().contains(this as ThreadLocal<Any>)) {
            Transmitter.holder.get().add(this as ThreadLocal<Any>)
        }
    }
}
