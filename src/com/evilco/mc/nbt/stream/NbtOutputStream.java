package com.evilco.mc.nbt.stream;

import com.evilco.mc.nbt.tag.ITag;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public class NbtOutputStream extends DataOutputStream {

	/**
	 * Constructs a new NbtOutputStream.
	 * @param out
	 */
	public NbtOutputStream (OutputStream out) {
		super (out);
	}

	/**
	 * Writes a tag into the stream.
	 * @param tag
	 * @throws IOException
	 */
	public void write (ITag tag) throws IOException {
		// write tag information
		this.writeByte (tag.getTagID ());

		// write tag
		tag.write (this, false);
	}
}