package com.tencent.bkrepo.common.api.thread

/**
 * 支持继承的可传播的threadLocal
 *
 * 适用用于父子线程和线程池传播
 * */
open class TransmittableInheritableThreadLocal<T> : InheritableThreadLocal<T>() {

    override fun set(value: T) {
        super.set(value)
        if (!Transmitter.holder.get().contains(this as ThreadLocal<Any>)) {
            Transmitter.holder.get().add(this as ThreadLocal<Any>)
        }
    }
}
