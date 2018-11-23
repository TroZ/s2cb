package com.evilco.mc.nbt.tag;

import com.evilco.mc.nbt.stream.NbtInputStream;
import com.evilco.mc.nbt.stream.NbtOutputStream;

import java.io.IOException;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public class TagFloat extends AbstractTag {

	/**
	 * Stores the tag value.
	 */
	protected float value;

	/**
	 * Constructs a new TagFloat.
	 * @param name
	 * @param value
	 */
	public TagFloat ( String name, float value) {
		super (name);
		this.setValue (value);
	}

	/**
	 * Constructs a new TagFloat.
	 * @param inputStream
	 * @param anonymous
	 * @throws IOException
	 */
	public TagFloat ( NbtInputStream inputStream, boolean anonymous) throws IOException {
		super (inputStream, anonymous);

		this.setValue (inputStream.readFloat ());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte getTagID () {
		return TagType.FLOAT.typeID;
	}

	/**
	 * Returns the tag value.
	 * @return
	 */
	public float getValue () {
		return this.value;
	}

	/**
	 * Sets a new tag value.
	 * @param f
	 */
	public void setValue (float f) {
		this.value = f;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write (NbtOutputStream outputStream, boolean anonymous) throws IOException {
		super.write (outputStream, anonymous);

		// write value
		outputStream.writeFloat (this.value);
	}
}