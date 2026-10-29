package octo.ui.window

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SegmentAllocator
import java.lang.foreign.StructLayout
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_DOUBLE
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The Objective-C runtime through the Foreign Function API, with just what Octo needs: messages
 * with object, integer, boolean and `NSRect` arguments. Apple silicon only, where `objc_msgSend`
 * returns structs itself and has no `_stret` variant.
 */
internal object ObjC {
    private val linker = Linker.nativeLinker()
    private val runtime = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global())
    private val getClass = function(runtime, "objc_getClass", FunctionDescriptor.of(ADDRESS, ADDRESS))
    private val registerName = function(runtime, "sel_registerName", FunctionDescriptor.of(ADDRESS, ADDRESS))
    private val msgSend = runtime.find("objc_msgSend").orElseThrow()
    private val sends = ConcurrentHashMap<FunctionDescriptor, MethodHandle>()

    /** `NSRect`: origin and size, four `CGFloat`s. */
    val RECT: StructLayout = MemoryLayout.structLayout(JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE)

    init {
        // A headless JVM, such as a test, has not loaded AppKit, so its classes would not resolve.
        SymbolLookup.libraryLookup(APPKIT, Arena.global())
    }

    fun cls(name: String): MemorySegment =
        Arena.ofConfined().use { getClass.invokeWithArguments(it.allocateFrom(name)) as MemorySegment }

    /** Sends a message that returns an object or nothing of interest. */
    fun send(
        receiver: MemorySegment,
        selector: String,
        vararg args: Any,
    ): MemorySegment = call(receiver, selector, ADDRESS, args) as MemorySegment

    fun sendLong(
        receiver: MemorySegment,
        selector: String,
        vararg args: Any,
    ): Long = call(receiver, selector, JAVA_LONG, args) as Long

    fun sendBool(
        receiver: MemorySegment,
        selector: String,
    ): Boolean = (call(receiver, selector, JAVA_BYTE, emptyArray()) as Byte).toInt() != 0

    /** Sends a message that returns an `NSRect`, allocated in [arena]. */
    fun sendRect(
        receiver: MemorySegment,
        selector: String,
        arena: SegmentAllocator,
    ): MemorySegment {
        val handle = handle(FunctionDescriptor.of(RECT, ADDRESS, ADDRESS))
        return handle.invokeWithArguments(arena, receiver, sel(selector)) as MemorySegment
    }

    private fun call(
        receiver: MemorySegment,
        selector: String,
        result: MemoryLayout,
        args: Array<out Any>,
    ): Any? {
        val layouts = listOf(ADDRESS, ADDRESS) + args.map(::layoutOf)
        val handle = handle(FunctionDescriptor.of(result, *layouts.toTypedArray()))
        return handle.invokeWithArguments(listOf(receiver, sel(selector)) + args.map(::valueOf))
    }

    private fun handle(descriptor: FunctionDescriptor): MethodHandle =
        sends.getOrPut(descriptor) { linker.downcallHandle(msgSend, descriptor) }

    private fun sel(name: String): MemorySegment =
        Arena.ofConfined().use { registerName.invokeWithArguments(it.allocateFrom(name)) as MemorySegment }

    private fun layoutOf(arg: Any): MemoryLayout =
        when (arg) {
            is Rect -> RECT
            is MemorySegment -> ADDRESS
            is Long -> JAVA_LONG
            is Boolean -> JAVA_BYTE
            else -> error("No native layout for ${arg::class}")
        }

    private fun valueOf(arg: Any): Any =
        when (arg) {
            is Rect -> arg.segment
            is Boolean -> (if (arg) 1 else 0).toByte()
            else -> arg
        }

    /** An `NSRect` passed by value, as `initWithFrame:` takes it. */
    class Rect(
        val segment: MemorySegment,
    )
}

/**
 * Runs work on AppKit's main thread, which is not Swing's event thread: AppKit objects such as
 * windows may only be changed there. Work is queued and never waited for, since AppKit sometimes
 * waits on the Swing thread itself.
 */
internal object AppKitThread {
    private val linker = Linker.nativeLinker()
    private val system = SymbolLookup.libraryLookup("/usr/lib/libSystem.B.dylib", Arena.global())
    private val dispatchAsync =
        function(system, "dispatch_async_f", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS))
    private val mainQueue = system.find("_dispatch_main_q").orElseThrow()
    private val queue = ConcurrentLinkedQueue<() -> Unit>()
    private val drain =
        linker.upcallStub(
            MethodHandles.lookup().findStatic(
                AppKitThread::class.java,
                "drain",
                MethodType.methodType(Void.TYPE, MemorySegment::class.java),
            ),
            FunctionDescriptor.ofVoid(ADDRESS),
            Arena.global(),
        )

    fun run(work: () -> Unit) {
        queue += work
        dispatchAsync.invokeWithArguments(mainQueue, MemorySegment.NULL, drain)
    }

    // An exception leaving an upcall ends the whole process, so nothing may escape here.
    @Suppress("TooGenericExceptionCaught")
    @JvmStatic
    fun drain(
        @Suppress("UNUSED_PARAMETER") context: MemorySegment,
    ) {
        while (true) {
            val work = queue.poll() ?: return
            try {
                work()
            } catch (e: Throwable) {
                warn("AppKit work failed", e)
            }
        }
    }
}

private const val APPKIT = "/System/Library/Frameworks/AppKit.framework/AppKit"

internal fun function(
    lookup: SymbolLookup,
    name: String,
    descriptor: FunctionDescriptor,
): MethodHandle = Linker.nativeLinker().downcallHandle(lookup.find(name).orElseThrow(), descriptor)

/** Native window code fails open: it says what went wrong and the window keeps working without it. */
internal fun warn(
    what: String,
    cause: Throwable,
) {
    System.err.println("Octo: $what, carrying on without it: $cause")
}
