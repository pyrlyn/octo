package octo.storage

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import octo.core.access.Device
import octo.core.access.Share
import octo.core.preset.Preset
import octo.core.preset.PresetMember
import octo.core.task.TaskId
import octo.core.triggers.TriggerRecord

// Each list's rows, in the order the list keeps them.

internal suspend fun AccessDao.replaceDevices(devices: List<Device>) {
    clearDevices()
    insertDevices(devices.mapIndexed { at, device -> DeviceRow(device.id, at, device.name, device.tokenHash) })
}

internal fun DeviceRow.toDevice() = Device(id, name, tokenHash)

internal suspend fun AccessDao.replaceShares(shares: List<Share>) {
    clearShares()
    insertShares(shares.mapIndexed { at, share -> ShareRow(share.id, at, share.taskId.value, share.tokenHash) })
}

internal fun ShareRow.toShare() = Share(id, TaskId(taskId), tokenHash)

private val memberList = ListSerializer(PresetMember.serializer())

internal suspend fun RecordDao.replacePresets(presets: List<Preset>) {
    clearPresets()
    insertPresets(
        presets.mapIndexed {
            at,
            preset,
            ->
            PresetRow(preset.name, at, json.encodeToString(memberList, preset.members))
        },
    )
}

internal fun PresetRow.toPreset() = Preset(name, json.decodeFromString(memberList, members))

private val deliveryList = ListSerializer(String.serializer())

internal suspend fun RecordDao.replaceTriggers(records: List<TriggerRecord>) {
    clearTriggers()
    insertTriggers(
        records.mapIndexed { at, record ->
            TriggerRow(record.id, at, record.lastRun, json.encodeToString(deliveryList, record.deliveries))
        },
    )
}

internal fun TriggerRow.toTrigger() = TriggerRecord(id, lastRun, json.decodeFromString(deliveryList, deliveries))
