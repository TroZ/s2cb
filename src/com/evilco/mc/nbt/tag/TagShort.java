package com.evilco.mc.nbt.tag;

import com.evilco.mc.nbt.stream.NbtInputStream;
import com.evilco.mc.nbt.stream.NbtOutputStream;

import java.io.IOException;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public class TagShort extends AbstractTag {

	/**
	 * Stores the tag value.
	 */
	protected short value;

	/**
	 * Constructs a new TagShort.
	 * @param name
	 * @param value
	 */
	public TagShort ( String name, short value) {
		super (name);
		this.setValue (value);
	}

	/**
	 * Constructs a new TagShort.
	 * @param inputStream
	 * @param anonymous
	 * @throws IOException
	 */
	public TagShort ( NbtInputStream inputStream, boolean anonymous) throws IOException {
		super (inputStream, anonymous);

		// read value
		this.setValue (inputStream.readShort ());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte getTagID () {
		return TagType.SHORT.typeID;
	}

	/**
	 * Returns the tag value.
	 * @return
	 */
	public short getValue () {
		return this.value;
	}

	/**
	 * Sets a new tag value.
	 * @param s
	 */
	public void setValue (short s) {
		this.value = s;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write (NbtOutputStream outputStream, boolean anonymous) throws IOException {
		super.write (outputStream, anonymous);

		// write value
		outputStream.writeShort (this.value);
	}
}