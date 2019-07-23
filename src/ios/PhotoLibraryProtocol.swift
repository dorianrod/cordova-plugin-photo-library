import Foundation
import WebKit

@objc(PhotoLibraryProtocol) class PhotoLibraryProtocol : WKPhotoAssetProtocol {
    static let PHOTO_LIBRARY_PROTOCOL = "cdvphotolibrary"
    static let DEFAULT_WIDTH = "512"
    static let DEFAULT_HEIGHT = "384"
    static let DEFAULT_QUALITY = "0.5"

    override init(urlSchemeTask: WKURLSchemeTask) {
        super.init(urlSchemeTask: urlSchemeTask);
        self.url = PhotoLibraryProtocol.transformURL(orig_url: self.orig_url);
    }
    
    static func transformURL( orig_url: URL? ) -> URL? {
        if let urlOrigin = orig_url {
            var urlS = urlOrigin.absoluteString;
            
            let range = urlS.range(of: PHOTO_LIBRARY_PROTOCOL);
            if range != nil {
               // let prefix = range!.lowerBound as Int;
                urlS = PHOTO_LIBRARY_PROTOCOL + ":/" + urlS.substring(from: range!.upperBound)

                return URL(string: urlS);
            }
        }
        return nil;
    }
    
    static func shouldManageTask(url: URL) -> Bool {
        let url = url.path;
        return url.hasPrefix("/" + PHOTO_LIBRARY_PROTOCOL);
    }
    
    override func manageTask() {
        if let url = self.url {
           
            if true {//url.path != "" {
                
                let urlComponents = URLComponents(url: url, resolvingAgainstBaseURL: false)
                let queryItems = urlComponents?.queryItems
                
                // Errors are 404 as android plugin only supports returning 404
                
                let photoId = queryItems?.filter({$0.name == "photoId"}).first?.value
                if photoId == nil {
                    self.sendErrorResponse(404, error: "Missing 'photoId' query parameter")
                    return
                }
                
                if !PhotoLibraryService.hasPermission() {
                    self.sendErrorResponse(404, error: PhotoLibraryService.PERMISSION_ERROR)
                    return
                }
                
                let service = PhotoLibraryService.instance
                
                if url.host?.lowercased() == "thumbnail" {
                    
                    let widthStr = queryItems?.filter({$0.name == "width"}).first?.value ?? PhotoLibraryProtocol.DEFAULT_WIDTH
                    let width = Int(widthStr)
                    if width == nil {
                        self.sendErrorResponse(404, error: "Incorrect 'width' query parameter")
                        return
                    }
                    
                    let heightStr = queryItems?.filter({$0.name == "height"}).first?.value ?? PhotoLibraryProtocol.DEFAULT_HEIGHT
                    let height = Int(heightStr)
                    if height == nil {
                        self.sendErrorResponse(404, error: "Incorrect 'height' query parameter")
                        return
                    }
                    
                    let qualityStr = queryItems?.filter({$0.name == "quality"}).first?.value ?? PhotoLibraryProtocol.DEFAULT_QUALITY
                    let quality = Float(qualityStr)
                    if quality == nil {
                        self.sendErrorResponse(404, error: "Incorrect 'quality' query parameter")
                        return
                    }
                    
                    concurrentQueue.addOperation {
                        service.getThumbnail(photoId!, thumbnailWidth: width!, thumbnailHeight: height!, quality: quality!) { (imageData) in
                            if (imageData == nil) {
                                self.sendErrorResponse(404, error: PhotoLibraryService.PERMISSION_ERROR)
                                return
                            }
                            self.sendResponseWithResponseCode(200, data: imageData!.data, mimeType: imageData!.mimeType)
                        }
                    }
                    
                    return
                    
                } else if url.host?.lowercased() == "photo" {
                    
                    concurrentQueue.addOperation {
                        service.getPhoto(photoId!) { (imageData) in
                            if (imageData == nil) {
                                self.sendErrorResponse(404, error: PhotoLibraryService.PERMISSION_ERROR)
                                return
                            }
                            self.sendResponseWithResponseCode(200, data: imageData!.data, mimeType: imageData!.mimeType)
                        }
                    }
                    
                    return
                    
                }
                
            }
        }
        
        let body = "URI not supported by PhotoLibrary"
        self.sendResponseWithResponseCode(404, data: body.data(using: String.Encoding.ascii), mimeType: nil)
    }
}
