import Foundation

@objc(PhotoLibrary) class PhotoLibrary : CDVPlugin {
    
    lazy var concurrentQueue: DispatchQueue = DispatchQueue(label: "photo-library.queue.plugin", qos: DispatchQoS.utility, attributes: [.concurrent])
    
    override func pluginInitialize() {
        
        // Do not call PhotoLibraryService here, as it will cause permission prompt to appear on app start.
        
        URLProtocol.registerClass(PhotoLibraryProtocol.self)
        
    }
    
    override func onMemoryWarning() {
        // self.service.stopCaching()
        NSLog("-- MEMORY WARNING --")
    }
    
    
    // Will sort by creation date
    func getLibrary(_ command: CDVInvokedUrlCommand) {
        concurrentQueue.async {
            
            if !PhotoLibraryService.hasPermission() {
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: PhotoLibraryService.PERMISSION_ERROR)
                self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
                return
            }
            
            let service = PhotoLibraryService.instance
            
            let options = command.arguments[0] as! NSDictionary
            
            
            let thumbnailWidth = options["thumbnailWidth"] as! Int
            let thumbnailHeight = options["thumbnailHeight"] as! Int
            let itemsInChunk = options["itemsInChunk"] as! Int
            let chunkTimeSec = options["chunkTimeSec"] as! Double
            let useOriginalFileNames = options["useOriginalFileNames"] as! Bool
            let includeAlbumData = options["includeAlbumData"] as! Bool
            let includeCloudData = options["includeCloudData"] as! Bool
            let includeVideos = options["includeVideos"] as! Bool
            let includeImages = options["includeImages"] as! Bool
            var id_search = options["id_search"] as? Int;
            var tsStart = options["dateStart"] as? Int;
            var tsEnd = options["dateEnd"] as? Int;
           
     
            /*
            let formatter           = DateFormatter();
            formatter.locale        = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat    = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
            */
            
            var dateStart: Date? = nil;
            var dateEnd: Date?  = nil;
            if let tsStart = tsStart {
                dateStart = Date(timeIntervalSince1970: TimeInterval(tsStart))
            }
            if let tsEnd = tsEnd {
                dateEnd = Date(timeIntervalSince1970: TimeInterval(tsEnd))
            }
            
            /*
            if !dateStartString.isEmpty {
                dateStart = formatter.date(from: dateStartString)!;
            }
            if !dateEndString.isEmpty {
                dateEnd = formatter.date(from: dateStartString)!;
            }*/
            
            
            func createResult (library: [NSDictionary], chunkNum: Int, isLastChunk: Bool, id_search: Int?) -> [String: AnyObject] {
                let result: NSDictionary = [
                    "id_search": id_search,
                    "chunkNum": chunkNum,
                    "isLastChunk": isLastChunk,
                    "library": library
                ]
                return result as! [String: AnyObject]
            }
            
            let getLibraryOptions = PhotoLibraryGetLibraryOptions(thumbnailWidth: thumbnailWidth,
                                                                  thumbnailHeight: thumbnailHeight,
                                                                  itemsInChunk: itemsInChunk,
                                                                  chunkTimeSec: chunkTimeSec,
                                                                  useOriginalFileNames: useOriginalFileNames,
                                                                  includeImages: includeImages,
                                                                  includeAlbumData: includeAlbumData,
                                                                  includeCloudData: includeCloudData,
                                                                  includeVideos: includeVideos,
                                                                  id_search: id_search,
                                                                  dateStart: dateStart,
                                                                  dateEnd: dateEnd)
            
            service.getLibrary(getLibraryOptions,
                               completion: { (library, chunkNum, isLastChunk) in
                                
                                let result = createResult(library: library, chunkNum: chunkNum, isLastChunk: isLastChunk, id_search: id_search)
                                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: result)
                                pluginResult!.setKeepCallbackAs(!isLastChunk)
                                self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
            }
            )
        }
    }
    
    func getAlbums(_ command: CDVInvokedUrlCommand) {
        concurrentQueue.async {
            
            if !PhotoLibraryService.hasPermission() {
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: PhotoLibraryService.PERMISSION_ERROR)
                self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
                return
            }
            
            let service = PhotoLibraryService.instance
            
            let albums = service.getAlbums()
            
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: albums)
            self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
            
        }
    }
    
    
    func isAuthorized(_ command: CDVInvokedUrlCommand) {
        concurrentQueue.async {
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: PhotoLibraryService.hasPermission())
            self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
        }
    }
    
    
    func getThumbnail(_ command: CDVInvokedUrlCommand) {
        concurrentQueue.async {
            
            if !PhotoLibraryService.hasPermission() {
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: PhotoLibraryService.PERMISSION_ERROR)
                self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
                return
            }
            
            let service = PhotoLibraryService.instance
            
            let photoId = command.arguments[0] as! String
            let options = command.arguments[1] as! NSDictionary
            let thumbnailWidth = options["thumbnailWidth"] as! Int
            let thumbnailHeight = options["thumbnailHeight"] as! Int
            let quality = options["quality"] as! Float
            
            service.getThumbnail(photoId, thumbnailWidth: thumbnailWidth, thumbnailHeight: thumbnailHeight, quality: quality) { (imageData) in
                
                let pluginResult = imageData != nil ?
                    CDVPluginResult(
                        status: CDVCommandStatus_OK,
                        messageAsMultipart: [imageData!.data, imageData!.mimeType])
                    :
                    CDVPluginResult(
                        status: CDVCommandStatus_ERROR,
                        messageAs: "Could not fetch the thumbnail")
                
                self.commandDelegate!.send(pluginResult, callbackId: command.callbackId )
                
            }
            
        }
    }
    
    func getPhoto(_ command: CDVInvokedUrlCommand) {
        concurrentQueue.async {
            
            if !PhotoLibraryService.hasPermission() {
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: PhotoLibraryService.PERMISSION_ERROR)
                self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
                return
            }
            
            let service = PhotoLibraryService.instance
            
            let photoId = command.arguments[0] as! String
            
            service.getPhoto(photoId) { (imageData) in
                
                let pluginResult = imageData != nil ?
                    CDVPluginResult(
                        status: CDVCommandStatus_OK,
                        messageAsMultipart: [imageData!.data, imageData!.mimeType])
                    :
                    CDVPluginResult(
                        status: CDVCommandStatus_ERROR,
                        messageAs: "Could not fetch the image")
                
                self.commandDelegate!.send(pluginResult, callbackId: command.callbackId    )
            }
            
        }
    }
    
    func getLibraryItem(_ command: CDVInvokedUrlCommand) {
        concurrentQueue.async {
            
            if !PhotoLibraryService.hasPermission() {
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: PhotoLibraryService.PERMISSION_ERROR)
                self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
                return
            }
            
            let service = PhotoLibraryService.instance
            let info = command.arguments[0] as! NSDictionary
            let mime_type = info["mimeType"] as! String
            service.getLibraryItem(info["id"] as! String, mimeType: mime_type, completion: { (base64: String?) in
                self.returnPictureData(callbackId: command.callbackId, base64: base64, mimeType: mime_type)
            })
        }
    }
    
    
    func returnPictureData(callbackId : String, base64: String?, mimeType: String?) {
        let pluginResult = (base64 != nil) ?
            CDVPluginResult(
                status: CDVCommandStatus_OK,
                messageAsMultipart: [base64!, mimeType!])
            :
            CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: "Could not fetch the image")
        
        self.commandDelegate!.send(pluginResult, callbackId: callbackId)
        
    }
    
    
    func stopCaching(_ command: CDVInvokedUrlCommand) {
        
        let service = PhotoLibraryService.instance
        
        service.stopCaching()
        
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
        self.commandDelegate!.send(pluginResult, callbackId: command.callbackId    )
        
    }
    
    func requestAuthorization(_ command: CDVInvokedUrlCommand) {
        
        let service = PhotoLibraryService.instance
        
        service.requestAuthorization({
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
            self.commandDelegate!.send(pluginResult, callbackId: command.callbackId    )
        }, failure: { (err) in
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: err)
            self.commandDelegate!.send(pluginResult, callbackId: command.callbackId    )
        })
        
    }
    
    func saveImage(_ command: CDVInvokedUrlCommand) {
        concurrentQueue.async {
            
            if !PhotoLibraryService.hasPermission() {
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: PhotoLibraryService.PERMISSION_ERROR)
                self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
                return
            }
            
            let service = PhotoLibraryService.instance
            
            let url = command.arguments[0] as! String
            let album = command.arguments[1] as! String
            
            service.saveImage(url, album: album) { (libraryItem: NSDictionary?, error: String?) in
                if (error != nil) {
                    let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: error)
                    self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
                } else {
                    let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: libraryItem as! [String: AnyObject]?)
                    self.commandDelegate!.send(pluginResult, callbackId: command.callbackId    )
                }
            }
            
        }
    }
    
    func saveVideo(_ command: CDVInvokedUrlCommand) {
        concurrentQueue.async {
            
            if !PhotoLibraryService.hasPermission() {
                let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: PhotoLibraryService.PERMISSION_ERROR)
                self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
                return
            }
            
            let service = PhotoLibraryService.instance
            
            let url = command.arguments[0] as! String
            let album = command.arguments[1] as! String
            
            service.saveVideo(url, album: album) { (url: URL?, error: String?) in
                if (error != nil) {
                    let pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: error)
                    self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
                } else {
                    let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
                    self.commandDelegate!.send(pluginResult, callbackId: command.callbackId    )
                }
            }
            
        }
    }
    
}
