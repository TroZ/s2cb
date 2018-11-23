package com.evilco.mc.nbt.tag;

import com.evilco.mc.nbt.stream.NbtInputStream;
import com.evilco.mc.nbt.stream.NbtOutputStream;

import java.io.IOException;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public class TagLong extends AbstractTag {

	/**
	 * Stores the tag value.
	 */
	protected long value;

	/**
	 * Constructs a new TagLong.
	 * @param name
	 * @param value
	 */
	public TagLong ( String name, long value) {
		super (name);
		this.setValue (value);
	}

	/**
	 * Constructs a new TagLong.
	 * @param inputStream
	 * @param anonymous
	 * @throws IOException
	 */
	public TagLong ( NbtInputStream inputStream, boolean anonymous) throws IOException {
		super (inputStream, anonymous);

		// read value
		this.setValue (inputStream.readLong ());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte getTagID () {
		return TagType.LONG.typeID;
	}

	/**
	 * Returns the tag value.
	 * @return
	 */
	public long getValue () {
		return this.value;
	}

	/**
	 * Sets a new tag value.
	 * @param l
	 * @return
	 */
	public void setValue (long l) {
		this.value = l;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write (NbtOutputStream outputStream, boolean anonymous) throws IOException {
		super.write (outputStream, anonymous);

		// write value
		outputStream.writeLong (this.value);
	}
}