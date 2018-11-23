package com.evilco.mc.nbt.tag;


/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public interface ITagContainer extends ITag {

	/**
	 * Removes a tag from the container.
	 * @param tag The tag.
	 * @return
	 */
	public void removeTag ( ITag tag);
}