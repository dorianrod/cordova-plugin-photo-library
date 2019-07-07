package com.terikon.cordova.photolibrary;

public class PhotoLibraryGetLibraryOptions {

 public final Long id_search;
 public final int itemsInChunk;
 public final double chunkTimeSec;
 public final boolean includeAlbumData;
 public final Long dateStart;
 public final Long dateEnd;

 public PhotoLibraryGetLibraryOptions(int itemsInChunk, double chunkTimeSec, boolean includeAlbumData, Long dateStart, Long dateEnd, Long id_search) {
  this.itemsInChunk = itemsInChunk;
  this.chunkTimeSec = chunkTimeSec;
  this.includeAlbumData = includeAlbumData;
  this.dateStart = dateStart;
  this.dateEnd = dateEnd;
  this.id_search = id_search;
 }

}