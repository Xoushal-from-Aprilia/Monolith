package com.monolith.app.core

import java.util.UUID

// the few helpers of the app that its relay and crypto code need, so the dashboard can use that code as is

class Keys(val priv: ByteArray, val pub: String)

fun now(): Long = System.currentTimeMillis()
fun nowSec(): Long = System.currentTimeMillis() / 1000
fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(24)
