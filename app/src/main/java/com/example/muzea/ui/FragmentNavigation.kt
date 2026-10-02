package com.example.muzea.ui

import androidx.fragment.app.Fragment
import com.example.muzea.R

/**
 * Открывает экран поверх текущего, не уничтожая его.
 *
 * Вкладки в MainActivity живут постоянно, поэтому вложенные экраны (новость,
 * переписка, настройки группы) не заменяют вкладку через replace(), а прячут
 * её и добавляются сверху. Назад — стандартный popBackStack() возвращает
 * вкладку в прежнем состоянии, без повторной загрузки.
 */
fun Fragment.openDetailScreen(fragment: Fragment) {
    parentFragmentManager.beginTransaction()
        .setReorderingAllowed(true)
        .hide(this)
        .add(R.id.fragment_container, fragment)
        .addToBackStack(null)
        .commit()
}
