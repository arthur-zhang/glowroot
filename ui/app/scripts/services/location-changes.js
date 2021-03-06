/*
 * Copyright 2015 the original author or authors.
 *
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
 */

/* global glowroot */

// watching for location changes is important for handling browser back/forward buttons
glowroot.factory('locationChanges', [
  '$location',
  '$timeout',
  function ($location, $timeout) {

    function on($scope, callback) {
      var path = $location.path();
      $scope.$on('$locationChangeSuccess', function () {
        if ($location.path() === path) {
          callback();
        }
      });
      callback();
    }

    return {
      on: on
    };
  }
]);
