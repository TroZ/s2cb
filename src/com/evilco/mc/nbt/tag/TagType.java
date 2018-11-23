package com.evilco.mc.nbt.tag;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public enum TagType {
	BYTE (1, TagByte.class),
	BYTE_ARRAY (7, TagByteArray.class),
	COMPOUND (10, TagCompound.class),
	DOUBLE (6, TagDouble.class),
	END (0, null),
	FLOAT (5, TagFloat.class),
	INTEGER (3, TagInteger.class),
	INTEGER_ARRAY (11, TagIntegerArray.class),
	LIST (9, TagList.class),
	LONG (4, TagLong.class),
	SHORT (2, TagShort.class),
	STRING (8, TagString.class);

	/**
	 * Static Initialization.
	 */
	static {
		// create map builder
		ImmutableMap.Builder<Byte, TagType> mapBuilder = new ImmutableMap.Builder<> ();

		// add all types
		for (TagType type : values ()) {
			mapBuilder.put (type.typeID, type);
		}

		// build map
		typeMap = mapBuilder.build ();
	}

	/**
	 * Stores an internal mapping between IDs and types.
	 */
	protected static final Map<Byte, TagType> typeMap;

	/**
	 * Defines the tag representation type.
	 */
	public final Class<? extends ITag> tagType;

	/**
	 * Stores the tag identifier.
	 */
	public final byte typeID;

	/**
	 * Constructs a new TagType.
	 * @param typeID
	 * @param type
	 */
	private TagType (int typeID, Class<? extends ITag> type) {
		this.typeID = ((byte) typeID);
		this.tagType = type;
	}

	/**
	 * Returns the correct type associated with the specified type ID.
	 * @param typeID
	 * @return
	 */
	public static TagType valueOf (byte typeID) {
		return typeMap.get (typeID);
	}
}