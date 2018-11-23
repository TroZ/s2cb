package com.evilco.mc.nbt.stream;

import com.evilco.mc.nbt.tag.ITag;
import com.evilco.mc.nbt.tag.TagType;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public class NbtInputStream extends DataInputStream {

	/**
	 * Constructs a new NbtInputStream.
	 * @param in
	 */
	public NbtInputStream (InputStream in) {
		super (in);
	}

	/**
	 * Reads a single tag.
	 * @return
	 * @throws IOException
	 */
	public ITag readTag () throws IOException {
		// remove first byte
		byte type = this.readByte ();

		// find type
		TagType tagType = TagType.valueOf (type);

		// verify
		if (tagType == null) throw new IOException ("Invalid NBT tag: Found unknown tag type " + type + ".");

		// END?
		if (tagType == TagType.END) return null;

		// read tag
		return this.readTag (tagType, false);
	}

	/**
	 * Reads a single tag.
	 * @param type
	 * @param anonymous
	 * @return
	 * @throws IOException
	 */
	public ITag readTag (TagType type, boolean anonymous) throws IOException {
		// find constructor
		Constructor<? extends ITag> constructor = null;

		try {
			constructor = type.tagType.getConstructor (NbtInputStream.class, boolean.class);
		} catch (NoSuchMethodException ex) {
			throw new IOException ("Invalid NBT implementation state: Type " + type.tagType.getName () + " has no de-serialization constructor.");
		}

		// create a new instance
		try {
			return constructor.newInstance (this, anonymous);
		} catch (Exception ex) {
			throw new IOException ("Invalid NBT implementation state: Type " + type.tagType.getName () + " in (" + this.getClass ().getName () + ") has no valid constructor: " + ex.getMessage (), ex);
		}
	}
}