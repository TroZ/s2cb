package com.evilco.mc.nbt.tag;

import com.evilco.mc.nbt.stream.NbtInputStream;
import com.evilco.mc.nbt.stream.NbtOutputStream;

import java.io.IOException;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public class TagByte extends AbstractTag {

	/**
	 * Stores the byte value.
	 */
	protected byte value;

	/**
	 * Constructs a new TagByte.
	 * @param name
	 * @param value
	 */
	public TagByte ( String name, byte value) {
		super (name);
		this.setValue (value);
	}

	/**
	 * Constructs a new TagByte.
	 * @param inputStream
	 * @param anonymous
	 * @throws IOException
	 */
	public TagByte ( NbtInputStream inputStream, boolean anonymous) throws IOException {
		super (inputStream, anonymous);

		// read value
		this.setValue (inputStream.readByte ());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte getTagID () {
		return TagType.BYTE.typeID;
	}

	/**
	 * Returns the current tag value.
	 * @return
	 */
	public byte getValue () {
		return this.value;
	}

	/**
	 * Sets a new byte value.
	 * @param b
	 */
	public void setValue (byte b) {
		this.value = b;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write (NbtOutputStream outputStream, boolean anonymous) throws IOException {
		super.write (outputStream, anonymous);

		// write value
		outputStream.write (this.getValue ());
	}
}