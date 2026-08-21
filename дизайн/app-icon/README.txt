ИКОНКА ПРИЛОЖЕНИЯ — куда класть файлы
======================================

Скопируй с заменой в модуль :app (app/src/main/res/):

  app-icon/drawable/ic_launcher_foreground.xml   ->  app/src/main/res/drawable/ic_launcher_foreground.xml   (замена)
  app-icon/drawable/ic_launcher_background.xml   ->  app/src/main/res/drawable/ic_launcher_background.xml   (новый файл)
  app-icon/mipmap-anydpi-v26/ic_launcher.xml     ->  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml     (замена)
  app-icon/values/colors.xml                     ->  app/src/main/res/values/colors.xml                     (замена)

Что изменилось:
- foreground: будильник стал ЧЁРНЫМ (был белый). Геометрия прежняя.
- background: теперь векторный drawable — сигнальный красный #FF0026 + полоса
  опасности из чёрных диагональных штрихов у верха. Раньше был ровный цвет.
- ic_launcher.xml: background теперь ссылается на drawable, а не на @color.

Если полоса опасности не нравится или её слишком обрезает маска лаунчера —
верни ровный фон: в ic_launcher.xml поменяй строку background на
  <background android:drawable="@color/ic_launcher_background" />
Цвет #FF0026 уже лежит в colors.xml.

Тёмная тема иконки (monochrome) использует тот же силуэт будильника.
