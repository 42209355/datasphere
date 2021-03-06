/*
 * Copyright 2019 WeBank
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.webank.wedatasphere.dss.common.utils;

import java.io.File;

/**
 * created by cooperyang on 2019/6/14
 * Description: FileHelper 是 linux 文件系统的帮助类，检查目录是否存在等
 */
public class FileHelper {

    public static boolean checkDirExists(String dir){
        File file = new File(dir);
        return file.exists() && file.isDirectory();
    }

}
