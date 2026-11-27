package com.nexus.prm.data

import android.content.Context
import android.provider.ContactsContract

data class DeviceContact(
  val contactId: String,
  val displayName: String,
  val phones: List<String>,
  val emails: List<String>,
)

object AndroidContactsReader {
  fun readContacts(context: Context): List<DeviceContact> {
    val resolver = context.contentResolver
    val contactsById = linkedMapOf<String, MutableDeviceContact>()

    resolver.query(
      ContactsContract.Contacts.CONTENT_URI,
      arrayOf(
        ContactsContract.Contacts._ID,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
      ),
      null,
      null,
      "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
    )?.use { cursor ->
      val idCol = cursor.getColumnIndex(ContactsContract.Contacts._ID)
      val nameCol = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
      while (cursor.moveToNext()) {
        val id = cursor.getString(idCol) ?: continue
        val name = cursor.getString(nameCol)?.trim().orEmpty()
        if (name.isNotEmpty()) {
          contactsById[id] = MutableDeviceContact(id, name)
        }
      }
    }

    if (contactsById.isEmpty()) return emptyList()

    resolver.query(
      ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
      arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
      ),
      null,
      null,
      null,
    )?.use { cursor ->
      val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
      val numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
      while (cursor.moveToNext()) {
        val id = cursor.getString(idCol) ?: continue
        val num = cursor.getString(numCol)?.trim().orEmpty()
        val target = contactsById[id] ?: continue
        if (num.isNotEmpty() && target.phones.none { DuplicateMatcher.normalizePhone(it) == DuplicateMatcher.normalizePhone(num) }) {
          target.phones += num
        }
      }
    }

    resolver.query(
      ContactsContract.CommonDataKinds.Email.CONTENT_URI,
      arrayOf(
        ContactsContract.CommonDataKinds.Email.CONTACT_ID,
        ContactsContract.CommonDataKinds.Email.ADDRESS,
      ),
      null,
      null,
      null,
    )?.use { cursor ->
      val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
      val emailCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
      while (cursor.moveToNext()) {
        val id = cursor.getString(idCol) ?: continue
        val addr = cursor.getString(emailCol)?.trim().orEmpty()
        val target = contactsById[id] ?: continue
        if (addr.isNotEmpty() && target.emails.none { it.equals(addr, ignoreCase = true) }) {
          target.emails += addr
        }
      }
    }

    return contactsById.values.map {
      DeviceContact(
        contactId = it.id,
        displayName = it.name,
        phones = it.phones,
        emails = it.emails,
      )
    }
  }

  private class MutableDeviceContact(
    val id: String,
    val name: String,
    val phones: MutableList<String> = mutableListOf(),
    val emails: MutableList<String> = mutableListOf(),
  )
}
